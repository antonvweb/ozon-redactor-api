package org.ozonLabel.ozonApi.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.ozonLabel.common.dto.datamatrix.*;
import org.ozonLabel.common.exception.user.ValidationException;
import org.ozonLabel.common.service.datamatrix.DataMatrixService;
import org.ozonLabel.common.service.user.CompanyService;
import org.ozonLabel.ozonApi.entity.DataMatrixCode;
import org.ozonLabel.ozonApi.entity.DataMatrixFile;
import org.ozonLabel.ozonApi.repository.DataMatrixCodeRepository;
import org.ozonLabel.ozonApi.repository.DataMatrixFileRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.google.zxing.*;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class DataMatrixServiceImpl implements DataMatrixService {

    private final DataMatrixCodeRepository dataMatrixCodeRepository;
    private final CompanyService companyService;
    private final DataMatrixFileRepository dataMatrixFileRepository;

    // ASCII коды для GS1
    private static final char FNC1 = (char) 232;  // FNC1 для DataMatrix
    private static final char GS = (char) 29;     // Group Separator

    @Override
    @Transactional
    public DataMatrixUploadResponse uploadCodes(
            String userEmail,
            Long companyOwnerId,
            Long productId,
            MultipartFile file,
            boolean checkDuplicates) {

        companyService.checkAccess(userEmail, companyOwnerId);

        String fileName = file.getOriginalFilename();
        if (fileName == null) {
            throw new ValidationException("Имя файла не указано");
        }

        List<String> allCodes;
        try {
            if (fileName.toLowerCase().endsWith(".pdf")) {
                allCodes = parsePdf(file.getInputStream());
            } else if (fileName.toLowerCase().endsWith(".csv")) {
                allCodes = parseCsv(file.getInputStream());
            } else {
                throw new ValidationException("Неподдерживаемый формат файла. Используйте PDF или CSV");
            }
        } catch (IOException e) {
            log.error("Ошибка при чтении файла: {}", e.getMessage());
            throw new ValidationException("Ошибка при чтении файла: " + e.getMessage());
        }

        int total = allCodes.size();
        int duplicates = 0;
        int newCodesCount = 0;
        List<String> uploadedCodes = new ArrayList<>();
        List<String> duplicateSourceFiles = new ArrayList<>();
        
        // Сохраняем originalContent - все коды из файла (до фильтрации)
        StringBuilder originalContentBuilder = new StringBuilder();
        for (String code : allCodes) {
            if (!code.trim().isEmpty()) {
                originalContentBuilder.append(code.trim()).append("\n");
            }
        }
        String originalContent = originalContentBuilder.toString();

        // Удаляем файлы старше 1 года
        LocalDateTime oneYearAgo = LocalDateTime.now().minusYears(1);
        List<DataMatrixFile> oldFiles = dataMatrixFileRepository.findOldFiles(companyOwnerId, oneYearAgo);
        if (!oldFiles.isEmpty()) {
            List<Long> oldFileIds = oldFiles.stream().map(DataMatrixFile::getId).toList();
            for (Long oldFileId : oldFileIds) {
                dataMatrixCodeRepository.deleteAllByFileId(oldFileId);
            }
            dataMatrixFileRepository.deleteAll(oldFiles);
            log.info("Удалено {} старых файлов (>1 года) для компании {}", oldFiles.size(), companyOwnerId);
        }

        // Создаём запись файла
        DataMatrixFile dataMatrixFile = DataMatrixFile.builder()
                .companyId(companyOwnerId)
                .userId(companyOwnerId)
                .productId(productId)
                .fileName(fileName)
                .uploadedAt(LocalDateTime.now())
                .totalCodes(total)
                .duplicateCount(0) // посчитаем позже
                .originalContent(originalContent)
                .build();
        dataMatrixFile = dataMatrixFileRepository.save(dataMatrixFile);
        Long fileId = dataMatrixFile.getId();

        for (String code : allCodes) {
            String trimmedCode = code.trim();
            if (trimmedCode.isEmpty()) {
                continue;
            }

            // Проверка на дубликат
            if (checkDuplicates && dataMatrixCodeRepository.existsByCompanyIdAndCode(companyOwnerId, trimmedCode)) {
                duplicates++;

                // Помечаем как дубликат и собираем имена файлов-источников
                Optional<DataMatrixCode> existingOpt = dataMatrixCodeRepository.findByCompanyIdAndCode(companyOwnerId, trimmedCode);
                if (existingOpt.isPresent()) {
                    DataMatrixCode existing = existingOpt.get();
                    if (!existing.getIsDuplicate()) {
                        existing.setIsDuplicate(true);
                        dataMatrixCodeRepository.save(existing);
                    }
                    // Находим файл-источник дубликата
                    if (existing.getFileId() != null) {
                        Optional<DataMatrixFile> sourceFileOpt = dataMatrixFileRepository.findById(existing.getFileId());
                        if (sourceFileOpt.isPresent()) {
                            String sourceFileName = sourceFileOpt.get().getFileName();
                            if (!duplicateSourceFiles.contains(sourceFileName)) {
                                duplicateSourceFiles.add(sourceFileName);
                            }
                        }
                    }
                }
                continue;
            }

            // Парсинг GS1 кода
            GS1DataMatrixResult parsed = parseGS1Code(trimmedCode);

            // Сохранение нового кода с fileId
            DataMatrixCode dataMatrixCode = DataMatrixCode.builder()
                    .userId(companyOwnerId)
                    .companyId(companyOwnerId)
                    .productId(productId)
                    .code(trimmedCode)
                    .gtin(parsed.gtin())
                    .serial(parsed.serial())
                    .isUsed(false)
                    .isDuplicate(false)
                    .fileId(fileId)
                    .build();

            dataMatrixCodeRepository.save(dataMatrixCode);
            uploadedCodes.add(trimmedCode);
            newCodesCount++;
        }

        // Обновляем duplicateCount в файле
        dataMatrixFile.setDuplicateCount(duplicates);
        dataMatrixFileRepository.save(dataMatrixFile);

        log.info("Загружено кодов: всего={}, новых={}, дубликатов={} для productId={}, fileId={}",
                total, newCodesCount, duplicates, productId, fileId);

        return DataMatrixUploadResponse.builder()
                .total(total)
                .newCodes(newCodesCount)
                .duplicates(duplicates)
                .codes(uploadedCodes)
                .duplicateSourceFiles(duplicateSourceFiles)
                .uploadedFileId(fileId)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<DataMatrixCodeDto> getCodesForProduct(
            String userEmail,
            Long companyOwnerId,
            Long productId,
            Pageable pageable) {
        
        companyService.checkAccess(userEmail, companyOwnerId);
        
        Page<DataMatrixCode> codes = dataMatrixCodeRepository.findByCompanyIdAndProductId(companyOwnerId, productId, pageable);
        return codes.map(this::mapToDto);
    }

    @Override
    @Transactional(readOnly = true)
    public DataMatrixStatsDto getStatsForProduct(
            String userEmail,
            Long companyOwnerId,
            Long productId) {
        
        companyService.checkAccess(userEmail, companyOwnerId);
        
        long total = dataMatrixCodeRepository.countByProductId(productId);
        long remaining = dataMatrixCodeRepository.countByProductIdAndIsUsedFalse(productId);
        long used = dataMatrixCodeRepository.countByProductIdAndIsUsedTrue(productId);
        
        return DataMatrixStatsDto.builder()
                .total(total)
                .remaining(remaining)
                .used(used)
                .build();
    }

    @Override
    @Transactional
    public void deleteCode(String userEmail, Long companyOwnerId, Long codeId) {
        companyService.checkAccess(userEmail, companyOwnerId);
        
        DataMatrixCode code = dataMatrixCodeRepository.findById(codeId)
                .orElseThrow(() -> new ValidationException("Код не найден"));
        
        if (!code.getCompanyId().equals(companyOwnerId)) {
            throw new ValidationException("Доступ запрещён");
        }
        
        if (code.getIsUsed()) {
            throw new ValidationException("Нельзя удалить использованный код");
        }
        
        dataMatrixCodeRepository.delete(code);
        log.info("Удалён код DataMatrix id={} пользователем {}", codeId, userEmail);
    }

    @Override
    @Transactional
    public Optional<String> reserveNextCodeForProduct(
            String userEmail,
            Long companyOwnerId,
            Long productId) {

        // Находим следующий неиспользованный код
        Optional<DataMatrixCode> codeOpt = dataMatrixCodeRepository.findFirstUnusedByProductId(productId);

        if (codeOpt.isEmpty()) {
            return Optional.empty();
        }

        DataMatrixCode code = codeOpt.get();

        // Проверяем принадлежность компании
        if (!code.getCompanyId().equals(companyOwnerId)) {
            throw new ValidationException("Код не принадлежит этой компании");
        }

        // Помечаем как использованный
        code.setIsUsed(true);
        code.setUsedAt(java.time.LocalDateTime.now());
        dataMatrixCodeRepository.save(code);

        return Optional.of(code.getCode());
    }

    @Override
    @Transactional
    public Optional<String> reserveNextCodeFromFile(
            String userEmail,
            Long companyOwnerId,
            Long fileId) {

        // Находим следующий неиспользованный код из файла
        Optional<DataMatrixCode> codeOpt = dataMatrixCodeRepository.findFirstUnusedByFileIdAndCompanyId(fileId, companyOwnerId);

        if (codeOpt.isEmpty()) {
            return Optional.empty();
        }

        DataMatrixCode code = codeOpt.get();

        // Помечаем как использованный
        code.setIsUsed(true);
        code.setUsedAt(java.time.LocalDateTime.now());
        dataMatrixCodeRepository.save(code);

        log.info("Зарезервирован код DataMatrix из файла {} пользователем {}", fileId, userEmail);

        return Optional.of(code.getCode());
    }

    @Override
    @Transactional(readOnly = true)
    public DataMatrixStatsDto getStatsForFile(
            String userEmail,
            Long companyOwnerId,
            Long fileId) {

        companyService.checkAccess(userEmail, companyOwnerId);

        // Проверяем существование файла и принадлежность компании
        DataMatrixFile file = dataMatrixFileRepository.findById(fileId)
                .orElseThrow(() -> new ValidationException("Файл не найден"));

        if (!file.getCompanyId().equals(companyOwnerId)) {
            throw new ValidationException("Доступ запрещён");
        }

        long total = dataMatrixCodeRepository.countByFileId(fileId);
        long remaining = dataMatrixCodeRepository.countByFileIdAndIsUsedFalse(fileId);
        long used = dataMatrixCodeRepository.countByFileIdAndIsUsedTrue(fileId);

        return DataMatrixStatsDto.builder()
                .total(total)
                .remaining(remaining)
                .used(used)
                .build();
    }

    @Override
    public GS1DataMatrixResult parseGS1Code(String rawCode) {
        // GS1 DataMatrix формат:
        // FNC1 + AI(01) + GTIN(14) + AI(21) + Serial + GS + AI(93) + Verification
        
        String gtin = null;
        String serial = null;
        String verificationKey = null;
        
        // Удаляем FNC1 в начале если есть
        String code = rawCode;
        if (code.startsWith(String.valueOf(FNC1))) {
            code = code.substring(1);
        }
        
        // Парсим AI (Application Identifiers)
        // AI(01) = 01 + 14 цифр GTIN
        // AI(21) = 21 + серийный номер (переменная длина)
        // GS (разделитель)
        // AI(93) = 93 + ключ проверки
        
        int pos = 0;
        while (pos < code.length()) {
            if (pos + 2 > code.length()) break;
            
            String ai = code.substring(pos, pos + 2);
            
            if ("01".equals(ai) && pos + 16 <= code.length()) {
                // GTIN: 14 цифр после AI(01)
                gtin = code.substring(pos + 2, pos + 16);
                pos += 16;
            } else if ("21".equals(ai)) {
                // Serial: переменная длина до GS или конца
                pos += 2;
                int gsIndex = code.indexOf(GS, pos);
                if (gsIndex != -1) {
                    serial = code.substring(pos, gsIndex);
                    pos = gsIndex + 1;
                } else {
                    serial = code.substring(pos);
                    break;
                }
            } else if ("93".equals(ai)) {
                // Verification key
                pos += 2;
                verificationKey = code.substring(pos);
                break;
            } else {
                pos++;
            }
        }
        
        return new GS1DataMatrixResult(gtin, serial, verificationKey);
    }

    /**
     * Парсинг PDF файла для извлечения DataMatrix кодов
     */
    private List<String> parsePdf(InputStream inputStream) throws IOException {
        List<String> codes = new ArrayList<>();
        
        try (PDDocument document = PDDocument.load(inputStream)) {
            PDFRenderer pdfRenderer = new PDFRenderer(document);
            
            for (int page = 0; page < document.getNumberOfPages(); page++) {
                // Рендерим страницу в изображение
                var image = pdfRenderer.renderImageWithDPI(page, 300, ImageType.RGB);
                
                // Пробуем распознать DataMatrix
                try {
                    LuminanceSource source = new BufferedImageLuminanceSource(image);
                    BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
                    
                    Result result = new MultiFormatReader().decode(bitmap, getHintMap());
                    String decodedText = result.getText();
                    
                    if (decodedText != null && !decodedText.trim().isEmpty()) {
                        codes.add(decodedText.trim());
                    }
                } catch (NotFoundException e) {
                    // DataMatrix не найден на странице, продолжаем
                    log.debug("DataMatrix не найден на странице {}", page);
                }
            }
        }
        
        return codes;
    }

    /**
     * Парсинг CSV файла
     */
    private List<String> parseCsv(InputStream inputStream) throws IOException {
        List<String> codes = new ArrayList<>();
        
        try (var reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                    codes.add(trimmed);
                }
            }
        }
        
        return codes;
    }

    /**
     * Настройки для ZXing decoder
     */
    private java.util.Map<DecodeHintType, Object> getHintMap() {
        var hints = new java.util.HashMap<DecodeHintType, Object>();
        hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        hints.put(DecodeHintType.POSSIBLE_FORMATS, 
            java.util.EnumSet.of(BarcodeFormat.DATA_MATRIX));
        hints.put(DecodeHintType.CHARACTER_SET, StandardCharsets.UTF_8);
        return hints;
    }

    private DataMatrixCodeDto mapToDto(DataMatrixCode code) {
        return DataMatrixCodeDto.builder()
                .id(code.getId())
                .code(code.getCode())
                .gtin(code.getGtin())
                .serial(code.getSerial())
                .isUsed(code.getIsUsed())
                .isDuplicate(code.getIsDuplicate())
                .usedAt(code.getUsedAt())
                .createdAt(code.getCreatedAt())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<DataMatrixFileDto> getFileHistory(
            String userEmail,
            Long companyOwnerId,
            Long productId) {

        companyService.checkAccess(userEmail, companyOwnerId);

        List<DataMatrixFile> files = dataMatrixFileRepository.findByCompanyIdAndProductIdOrderByUploadedAtDesc(companyOwnerId, productId);

        return files.stream()
                .map(this::mapToFileDto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] downloadFileAsCSV(
            String userEmail,
            Long companyOwnerId,
            Long fileId) {

        companyService.checkAccess(userEmail, companyOwnerId);

        DataMatrixFile file = dataMatrixFileRepository.findById(fileId)
                .orElseThrow(() -> new ValidationException("Файл не найден"));

        if (!file.getCompanyId().equals(companyOwnerId)) {
            throw new ValidationException("Доступ запрещён");
        }

        if (file.getOriginalContent() == null) {
            throw new ValidationException("Содержимое файла недоступно");
        }

        return file.getOriginalContent().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] downloadDuplicatesAsCSV(
            String userEmail,
            Long companyOwnerId,
            Long fileId) {

        companyService.checkAccess(userEmail, companyOwnerId);

        DataMatrixFile file = dataMatrixFileRepository.findById(fileId)
                .orElseThrow(() -> new ValidationException("Файл не найден"));

        if (!file.getCompanyId().equals(companyOwnerId)) {
            throw new ValidationException("Доступ запрещён");
        }

        List<DataMatrixCode> duplicateCodes = dataMatrixCodeRepository.findByFileId(fileId)
                .stream()
                .filter(DataMatrixCode::getIsDuplicate)
                .toList();

        StringBuilder csvBuilder = new StringBuilder();
        for (DataMatrixCode code : duplicateCodes) {
            csvBuilder.append(code.getCode()).append("\n");
        }

        return csvBuilder.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    @Transactional
    public DeleteFileResponse deleteFile(
            String userEmail,
            Long companyOwnerId,
            Long fileId) {

        companyService.checkAccess(userEmail, companyOwnerId);

        DataMatrixFile file = dataMatrixFileRepository.findById(fileId)
                .orElseThrow(() -> new ValidationException("Файл не найден"));

        if (!file.getCompanyId().equals(companyOwnerId)) {
            throw new ValidationException("Доступ запрещён");
        }

        // Находим все коды файла
        List<DataMatrixCode> fileCodes = dataMatrixCodeRepository.findByFileId(fileId);
        int deletedCodes = fileCodes.size();

        // Удаляем все коды файла
        dataMatrixCodeRepository.deleteAllByFileId(fileId);

        // Пересчитываем дубликаты: находим коды, у которых isDuplicate=true,
        // но теперь в пуле осталась только 1 запись с таким значением
        List<String> orphanDuplicateCodes = dataMatrixCodeRepository.findOrphanDuplicateCodes(companyOwnerId);
        int resolvedDuplicates = orphanDuplicateCodes.size();

        if (!orphanDuplicateCodes.isEmpty()) {
            dataMatrixCodeRepository.clearDuplicateFlag(companyOwnerId, orphanDuplicateCodes);
        }

        // Удаляем файл
        dataMatrixFileRepository.delete(file);

        log.info("Удалён файл {} с {} кодами, снято {} дубликатов пользователем {}",
                fileId, deletedCodes, resolvedDuplicates, userEmail);

        return DeleteFileResponse.builder()
                .deletedCodes(deletedCodes)
                .resolvedDuplicates(resolvedDuplicates)
                .message("Файл успешно удалён")
                .build();
    }

    private DataMatrixFileDto mapToFileDto(DataMatrixFile file) {
        return DataMatrixFileDto.builder()
                .id(file.getId())
                .fileName(file.getFileName())
                .uploadedAt(file.getUploadedAt())
                .totalCodes(file.getTotalCodes())
                .duplicateCount(file.getDuplicateCount())
                .duplicateSources(new ArrayList<>()) // Можно расширить при необходимости
                .build();
    }
}
