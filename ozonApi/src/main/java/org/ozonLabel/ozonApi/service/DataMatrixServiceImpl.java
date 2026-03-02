package org.ozonLabel.ozonApi.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.ozonLabel.common.dto.datamatrix.DataMatrixCodeDto;
import org.ozonLabel.common.dto.datamatrix.DataMatrixStatsDto;
import org.ozonLabel.common.dto.datamatrix.DataMatrixUploadResponse;
import org.ozonLabel.common.exception.user.ValidationException;
import org.ozonLabel.common.service.datamatrix.DataMatrixService;
import org.ozonLabel.common.service.user.CompanyService;
import org.ozonLabel.ozonApi.entity.DataMatrixCode;
import org.ozonLabel.ozonApi.repository.DataMatrixCodeRepository;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class DataMatrixServiceImpl implements DataMatrixService {

    private final DataMatrixCodeRepository dataMatrixCodeRepository;
    private final CompanyService companyService;

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
        
        List<String> codes;
        try {
            if (fileName.toLowerCase().endsWith(".pdf")) {
                codes = parsePdf(file.getInputStream());
            } else if (fileName.toLowerCase().endsWith(".csv")) {
                codes = parseCsv(file.getInputStream());
            } else {
                throw new ValidationException("Неподдерживаемый формат файла. Используйте PDF или CSV");
            }
        } catch (IOException e) {
            log.error("Ошибка при чтении файла: {}", e.getMessage());
            throw new ValidationException("Ошибка при чтении файла: " + e.getMessage());
        }
        
        int total = codes.size();
        int duplicates = 0;
        int newCodes = 0;
        List<String> uploadedCodes = new ArrayList<>();
        
        for (String code : codes) {
            String trimmedCode = code.trim();
            if (trimmedCode.isEmpty()) {
                continue;
            }
            
            // Проверка на дубликат
            if (checkDuplicates && dataMatrixCodeRepository.existsByCompanyIdAndCode(companyOwnerId, trimmedCode)) {
                duplicates++;
                
                // Помечаем как дубликат
                Optional<DataMatrixCode> existingOpt = dataMatrixCodeRepository.findByCompanyIdAndCode(companyOwnerId, trimmedCode);
                if (existingOpt.isPresent()) {
                    DataMatrixCode existing = existingOpt.get();
                    if (!existing.getIsDuplicate()) {
                        existing.setIsDuplicate(true);
                        dataMatrixCodeRepository.save(existing);
                    }
                }
                continue;
            }
            
            // Парсинг GS1 кода
            GS1DataMatrixResult parsed = parseGS1Code(trimmedCode);
            
            // Сохранение нового кода
            DataMatrixCode dataMatrixCode = DataMatrixCode.builder()
                    .userId(companyOwnerId)
                    .companyId(companyOwnerId)
                    .productId(productId)
                    .code(trimmedCode)
                    .gtin(parsed.gtin())
                    .serial(parsed.serial())
                    .isUsed(false)
                    .isDuplicate(false)
                    .build();
            
            dataMatrixCodeRepository.save(dataMatrixCode);
            uploadedCodes.add(trimmedCode);
            newCodes++;
        }
        
        log.info("Загружено кодов: всего={}, новых={}, дубликатов={} для productId={}", 
                total, newCodes, duplicates, productId);
        
        return DataMatrixUploadResponse.builder()
                .total(total)
                .newCodes(newCodes)
                .duplicates(duplicates)
                .codes(uploadedCodes)
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
}
