package org.ozonLabel.ozonApi.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.ozonLabel.common.dto.label.*;
import org.ozonLabel.common.dto.ozon.CreateProductBySizeDto;
import org.ozonLabel.common.dto.ozon.ExcelImportResult;
import org.ozonLabel.common.dto.ozon.ProductInfo;
import org.ozonLabel.common.dto.user.UserResponseDto;
import org.ozonLabel.common.exception.user.ResourceNotFoundException;
import org.ozonLabel.common.model.SourceType;
import org.ozonLabel.common.service.label.LabelService;
import org.ozonLabel.common.service.ozon.FolderService;
import org.ozonLabel.common.service.ozon.OzonService;
import org.ozonLabel.common.service.ozon.ProductCreationService;
import org.ozonLabel.common.service.user.UserService;
import org.ozonLabel.ozonApi.entity.OzonProduct;
import org.ozonLabel.ozonApi.entity.ProductFolder;
import org.ozonLabel.ozonApi.repository.OzonProductRepository;
import org.ozonLabel.ozonApi.repository.ProductFolderRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductCreationServiceIml implements ProductCreationService {
    private final OzonService ozonService;
    private final UserService userService;
    private final FolderService folderService;
    private final ObjectMapper objectMapper;
    private final LabelService labelService;
    private final OzonProductRepository ozonProductRepository;
    private final ProductFolderRepository productFolderRepository;

    @Override
    @Transactional
    public ProductInfo createProductBySize(String userEmail, CreateProductBySizeDto dto) {
        UserResponseDto user = getUserByEmail(userEmail);

        if (dto.getSize() == null || dto.getSize().trim().isEmpty()) {
            throw new IllegalArgumentException("Размер не может быть пустым");
        }

        if (dto.getFolderId() != null) {
            validateFolder(user.getId(), dto.getFolderId());
        }

        Long productId = generateLocalProductId();
        while (ozonService.existsByUserIdAndProductId(user.getId(), productId)) {
            productId = generateLocalProductId();
        }

        OzonProduct product = OzonProduct.builder()
                .userId(user.getId())
                .productId(productId)
                .size(dto.getSize().trim())
                .folderId(dto.getFolderId())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        ProductInfo productInfo = mapToProductInfo(product);


        ProductInfo savedProductInfo = ozonService.saveProduct(productInfo);
        log.info("сохраненный ProductInfo '{}', product -  {} productInfo -  {}",
                dto.getSize(), productId, userEmail);

        log.info("Создан товар по размеру '{}' (product_id: {}) для пользователя {} в папке {}",
                dto.getSize(), productId, userEmail, dto.getFolderId());

        return savedProductInfo;
    }

    @Override
    @Transactional
    public ExcelImportResult importFromExcel(String userEmail, Long companyOwnerId, MultipartFile file, Long folderId) {
        // 1. Валидация файла
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Файл не может быть пустым");
        }

        String filename = file.getOriginalFilename();
        if (filename == null || (!filename.endsWith(".xlsx") && !filename.endsWith(".xls"))) {
            throw new IllegalArgumentException("Поддерживаются только Excel файлы (.xlsx, .xls)");
        }

        // 2. Проверка доступа
        userService.findByEmail(userEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Пользователь не найден"));

        // 3. Открытие файла через Apache POI
        Workbook workbook;
        try {
            workbook = WorkbookFactory.create(file.getInputStream());
        } catch (IOException e) {
            throw new IllegalArgumentException("Ошибка чтения Excel файла: " + e.getMessage(), e);
        }

        try {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null || sheet.getPhysicalNumberOfRows() == 0) {
                throw new IllegalArgumentException("Файл не содержит данных");
            }

            // 4. Чтение заголовков (строка 0)
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                throw new IllegalArgumentException("Файл не содержит заголовков");
            }

            List<String> columnNames = new ArrayList<>();
            int lastCellNum = headerRow.getLastCellNum();
            for (int i = 0; i < lastCellNum; i++) {
                Cell cell = headerRow.getCell(i);
                String colName = getCellValueAsString(cell).trim();
                columnNames.add(colName);
            }

            // 5. Определение индексов специальных колонок
            int barcodeColIndex = columnNames.indexOf("Штрихкод");
            int nameColIndex = columnNames.indexOf("Наименование");

            // 6. Создание папки если не передана
            if (folderId == null) {
                String folderName = filename.substring(0, filename.lastIndexOf('.'));
                ProductFolder folder = ProductFolder.builder()
                        .userId(companyOwnerId)
                        .name(folderName)
                        .sourceType(SourceType.EXCEL)
                        .sourceFileName(filename)
                        .build();
                ProductFolder savedFolder = productFolderRepository.save(folder);
                folderId = savedFolder.getId();
            } else {
                validateFolder(companyOwnerId, folderId);
            }

            // 7. Обработка строк данных
            List<Long> productIds = new ArrayList<>();
            List<String> errors = new ArrayList<>();
            int importedCount = 0;
            int skippedCount = 0;

            int firstRowNum = sheet.getFirstRowNum();
            int lastRowNum = sheet.getLastRowNum();

            for (int rowNum = firstRowNum + 1; rowNum <= lastRowNum; rowNum++) {
                Row row = sheet.getRow(rowNum);
                if (row == null) {
                    skippedCount++;
                    continue;
                }

                // Проверка: все ли ячейки пустые
                boolean allEmpty = true;
                for (int i = 0; i < columnNames.size(); i++) {
                    Cell cell = row.getCell(i);
                    if (cell != null && !getCellValueAsString(cell).trim().isEmpty()) {
                        allEmpty = false;
                        break;
                    }
                }

                if (allEmpty) {
                    skippedCount++;
                    continue;
                }

                try {
                    // b. Чтение значений
                    Map<String, String> rowData = new LinkedHashMap<>();
                    for (int i = 0; i < columnNames.size(); i++) {
                        Cell cell = row.getCell(i);
                        String value = getCellValueAsString(cell).trim();
                        rowData.put(columnNames.get(i), value);
                    }

                    // c. Извлечение штрихкода
                    String barcode = barcodeColIndex >= 0 ? rowData.get("Штрихкод") : null;
                    if (barcode != null) {
                        barcode = barcode.trim();
                        if (barcode.isEmpty()) {
                            barcode = null;
                        }
                    }

                    // d. Извлечение названия
                    String name = nameColIndex >= 0 ? rowData.get("Наименование") : null;
                    if (name == null || name.trim().isEmpty()) {
                        name = "Товар " + (rowNum + 1);
                    } else {
                        name = name.trim();
                    }

                    // e. Генерация productId
                    Long productId = generateLocalProductId();
                    while (ozonService.existsByUserIdAndProductId(companyOwnerId, productId)) {
                        productId = generateLocalProductId();
                    }

                    // f. Создание OzonProduct
                    String barcodesJson = barcode != null
                            ? objectMapper.writeValueAsString(List.of(barcode))
                            : null;

                    OzonProduct product = OzonProduct.builder()
                            .userId(companyOwnerId)
                            .productId(productId)
                            .name(name)
                            .barcodes(barcodesJson)
                            .folderId(folderId)
                            .excelData(objectMapper.writeValueAsString(rowData))
                            .createdAt(LocalDateTime.now())
                            .updatedAt(LocalDateTime.now())
                            .build();

                    ozonProductRepository.save(product);
                    productIds.add(productId);
                    importedCount++;

                    // g. Создание Label с динамическими слоями
                    createLabelForExcelProduct(userEmail, companyOwnerId, product, columnNames, barcode, name);

                } catch (Exception e) {
                    log.error("Ошибка обработки строки {}: {}", rowNum + 1, e.getMessage(), e);
                    errors.add("Строка " + (rowNum + 1) + ": " + e.getMessage());
                    skippedCount++;
                }
            }

            // Получение имени папки
            ProductFolder folder = productFolderRepository.findById(folderId)
                    .orElseThrow(() -> new ResourceNotFoundException("Папка не найдена"));

            return ExcelImportResult.builder()
                    .totalRows(lastRowNum - firstRowNum)
                    .importedCount(importedCount)
                    .skippedCount(skippedCount)
                    .productIds(productIds)
                    .columnNames(columnNames)
                    .folderName(folder.getName())
                    .folderId(folderId)
                    .errors(errors)
                    .build();

        } finally {
            try {
                workbook.close();
            } catch (IOException e) {
                log.warn("Ошибка закрытия workbook: {}", e.getMessage());
            }
        }
    }

    @Override
    @Transactional
    public ExcelImportResult updateExcelFile(String userEmail, Long companyOwnerId, Long folderId, MultipartFile file) {
        // 1. Валидация файла
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Файл не может быть пустым");
        }

        String filename = file.getOriginalFilename();
        if (filename == null || (!filename.endsWith(".xlsx") && !filename.endsWith(".xls"))) {
            throw new IllegalArgumentException("Поддерживаются только Excel файлы (.xlsx, .xls)");
        }

        // 2. Найти папку и проверить sourceType
        ProductFolder folder = productFolderRepository.findById(folderId)
                .orElseThrow(() -> new ResourceNotFoundException("Папка не найдена"));

        if (!folder.getUserId().equals(companyOwnerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Нет доступа к папке");
        }

        if (folder.getSourceType() != SourceType.EXCEL) {
            throw new IllegalArgumentException("Папка не была создана из Excel файла");
        }

        // 3. Открытие файла
        Workbook workbook;
        try {
            workbook = WorkbookFactory.create(file.getInputStream());
        } catch (IOException e) {
            throw new IllegalArgumentException("Ошибка чтения Excel файла: " + e.getMessage(), e);
        }

        try {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null || sheet.getPhysicalNumberOfRows() == 0) {
                throw new IllegalArgumentException("Файл не содержит данных");
            }

            // 4. Чтение заголовков
            Row headerRow = sheet.getRow(0);
            List<String> columnNames = new ArrayList<>();
            int lastCellNum = headerRow.getLastCellNum();
            for (int i = 0; i < lastCellNum; i++) {
                Cell cell = headerRow.getCell(i);
                String colName = getCellValueAsString(cell).trim();
                columnNames.add(colName);
            }

            int barcodeColIndex = columnNames.indexOf("Штрихкод");
            int nameColIndex = columnNames.indexOf("Наименование");

            // 5. Получить все существующие товары папки
            List<OzonProduct> existingProducts = ozonProductRepository.findByUserIdAndFolderId(companyOwnerId, folderId);
            Map<String, OzonProduct> productsByBarcode = new HashMap<>();
            for (OzonProduct product : existingProducts) {
                if (product.getBarcodes() != null) {
                    try {
                        List<String> barcodes = objectMapper.readValue(product.getBarcodes(), new TypeReference<List<String>>() {});
                        for (String bc : barcodes) {
                            productsByBarcode.put(bc, product);
                        }
                    } catch (Exception e) {
                        log.warn("Ошибка парсинга баркодов: {}", e.getMessage());
                    }
                }
            }

            // 6. Обработка строк нового файла
            List<Long> productIds = new ArrayList<>();
            List<String> errors = new ArrayList<>();
            int importedCount = 0;
            int skippedCount = 0;
            int updatedCount = 0;

            int firstRowNum = sheet.getFirstRowNum();
            int lastRowNum = sheet.getLastRowNum();

            for (int rowNum = firstRowNum + 1; rowNum <= lastRowNum; rowNum++) {
                Row row = sheet.getRow(rowNum);
                if (row == null) {
                    skippedCount++;
                    continue;
                }

                boolean allEmpty = true;
                for (int i = 0; i < columnNames.size(); i++) {
                    Cell cell = row.getCell(i);
                    if (cell != null && !getCellValueAsString(cell).trim().isEmpty()) {
                        allEmpty = false;
                        break;
                    }
                }

                if (allEmpty) {
                    skippedCount++;
                    continue;
                }

                try {
                    Map<String, String> rowData = new LinkedHashMap<>();
                    for (int i = 0; i < columnNames.size(); i++) {
                        Cell cell = row.getCell(i);
                        String value = getCellValueAsString(cell).trim();
                        rowData.put(columnNames.get(i), value);
                    }

                    String barcode = barcodeColIndex >= 0 ? rowData.get("Штрихкод") : null;
                    if (barcode != null && barcode.trim().isEmpty()) {
                        barcode = null;
                    }

                    String name = nameColIndex >= 0 ? rowData.get("Наименование") : null;
                    if (name == null || name.trim().isEmpty()) {
                        name = "Товар " + (rowNum + 1);
                    } else {
                        name = name.trim();
                    }

                    // Поиск товара по штрихкоду
                    OzonProduct product = null;
                    if (barcode != null && productsByBarcode.containsKey(barcode)) {
                        product = productsByBarcode.get(barcode);
                        // Обновить существующий товар
                        product.setName(name);
                        product.setExcelData(objectMapper.writeValueAsString(rowData));
                        product.setUpdatedAt(LocalDateTime.now());
                        ozonProductRepository.save(product);
                        updatedCount++;
                        importedCount++;
                    } else {
                        // Создать новый товар
                        Long productId = generateLocalProductId();
                        while (ozonService.existsByUserIdAndProductId(companyOwnerId, productId)) {
                            productId = generateLocalProductId();
                        }

                        String barcodesJson = barcode != null
                                ? objectMapper.writeValueAsString(List.of(barcode))
                                : null;

                        product = OzonProduct.builder()
                                .userId(companyOwnerId)
                                .productId(productId)
                                .name(name)
                                .barcodes(barcodesJson)
                                .folderId(folderId)
                                .excelData(objectMapper.writeValueAsString(rowData))
                                .createdAt(LocalDateTime.now())
                                .updatedAt(LocalDateTime.now())
                                .build();

                        ozonProductRepository.save(product);
                        productIds.add(productId);
                        importedCount++;

                        // Создать Label для нового товара
                        createLabelForExcelProduct(userEmail, companyOwnerId, product, columnNames, barcode, name);
                    }

                } catch (Exception e) {
                    log.error("Ошибка обработки строки {}: {}", rowNum + 1, e.getMessage(), e);
                    errors.add("Строка " + (rowNum + 1) + ": " + e.getMessage());
                    skippedCount++;
                }
            }

            // Обновить sourceFileName папки
            folder.setSourceFileName(filename);
            folder.setUpdatedAt(LocalDateTime.now());
            productFolderRepository.save(folder);

            return ExcelImportResult.builder()
                    .totalRows(lastRowNum - firstRowNum)
                    .importedCount(importedCount)
                    .skippedCount(skippedCount)
                    .productIds(productIds)
                    .columnNames(columnNames)
                    .folderName(folder.getName())
                    .folderId(folderId)
                    .errors(errors)
                    .build();

        } finally {
            try {
                workbook.close();
            } catch (IOException e) {
                log.warn("Ошибка закрытия workbook: {}", e.getMessage());
            }
        }
    }

    /**
     * Создать этикетку для товара из Excel с динамическими слоями
     */
    private void createLabelForExcelProduct(String userEmail, Long companyOwnerId, OzonProduct product,
                                             List<String> columnNames, String barcode, String name) {
        // Создаём слои: слой 0 (фон) + по одному dynamic слою на каждую колонку
        List<LayerDto> layers = new ArrayList<>();
        layers.add(LayerDto.builder()
                .id(0)
                .name("Canvas (Background)")
                .locked(true)
                .visible(true)
                .layerType("static")
                .build());

        List<ElementDto> elements = new ArrayList<>();

        for (int i = 0; i < columnNames.size(); i++) {
            String colName = columnNames.get(i);
            int layerId = i + 1;

            layers.add(LayerDto.builder()
                    .id(layerId)
                    .name(colName)
                    .locked(false)
                    .visible(true)
                    .layerType("dynamic")
                    .columnName(colName)
                    .build());
        }

        // Если есть колонка Штрихкод — добавить barcode элемент
        // Ширина: 75% от 58мм = ~43мм, отступ сверху: 5мм
        int barcodeLayerIdx = columnNames.indexOf("Штрихкод");
        if (barcode != null && !barcode.isEmpty() && barcodeLayerIdx >= 0) {
            elements.add(ElementDto.builder()
                    .id("barcode-1")
                    .name("Штрихкод")
                    .type("barcode")
                    .layerId(barcodeLayerIdx + 1) // Слой = индекс колонки + 1
                    .x(new BigDecimal("7.5"))
                    .y(new BigDecimal("5"))
                    .width(new BigDecimal("43"))
                    .height(new BigDecimal("15"))
                    .barcodeType("Code 128")
                    .content(barcode)
                    .zIndex(1)
                    .visible(true)
                    .build());
        }

        // Если есть колонка Наименование — добавить text элемент
        int nameLayerIdx = columnNames.indexOf("Наименование");
        if (nameLayerIdx >= 0) {
            elements.add(ElementDto.builder()
                    .id("name-1")
                    .name("Наименование")
                    .type("text")
                    .layerId(nameLayerIdx + 1)
                    .x(new BigDecimal("3"))
                    .y(new BigDecimal("22"))
                    .width(new BigDecimal("52"))
                    .height(new BigDecimal("10"))
                    .content(name)
                    .style(TextStyleDto.builder()
                            .fontFamily("Arial")
                            .fontSize(new BigDecimal("10"))
                            .textAlign("left")
                            .build())
                    .zIndex(2)
                    .visible(true)
                    .build());
        }

        LabelConfigDto config = LabelConfigDto.builder()
                .width(new BigDecimal("58"))
                .height(new BigDecimal("40"))
                .unit("mm")
                .layers(layers)
                .elements(elements)
                .build();

        CreateLabelDto createDto = CreateLabelDto.builder()
                .productId(product.getProductId())
                .name(name)
                .config(config)
                .build();

        labelService.createLabel(userEmail, companyOwnerId, createDto);
        log.info("Создана этикетка для товара {} (productId: {})", name, product.getProductId());
    }

    /**
     * Получить значение ячейки как строку
     */
    private String getCellValueAsString(Cell cell) {
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> {
                // Если целое число — без дробной части (штрихкоды!)
                double val = cell.getNumericCellValue();
                yield val == Math.floor(val) ? String.valueOf((long) val) : String.valueOf(val);
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> {
                try {
                    yield String.valueOf(cell.getNumericCellValue());
                } catch (Exception e) {
                    yield cell.getStringCellValue();
                }
            }
            default -> "";
        };
    }

    private Long generateLocalProductId() {
        return -Math.abs(System.currentTimeMillis() % 1_000_000_000L);
    }

    private void validateFolder(Long userId, Long folderId) {
        if (!folderService.existsByUserIdAndId(userId, folderId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Папка не найдена");
        }
    }

    private UserResponseDto getUserByEmail(String email) {
        return userService.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Пользователь не найден"));
    }

    /**
     * Преобразует сущность OzonProduct в DTO ProductInfo
     */
    private ProductInfo mapToProductInfo(OzonProduct product) {
        if (product == null) return null;

        ProductInfo info = new ProductInfo();
        info.setUserId(product.getUserId());
        info.setId(product.getProductId());
        info.setName(product.getName());
        info.setSku(product.getSku());
        if (product.getTags() != null) {
            try {
                List<String> tags = objectMapper.readValue(product.getTags(), new TypeReference<List<String>>() {});
                info.setTags(tags);
            } catch (Exception e) {
                log.warn("Ошибка при парсинге тегов: {}", product.getTags(), e);
                info.setTags(new ArrayList<>());
            }
        } else {
            info.setTags(new ArrayList<>());
        }
        info.setOfferId(product.getOfferId());
        info.setIsArchived(product.getIsArchived());
        info.setIsAutoarchived(product.getIsAutoarchived());
        info.setPrice(product.getPrice() != null ? product.getPrice().toString() : null);
        info.setOldPrice(product.getOldPrice() != null ? product.getOldPrice().toString() : null);
        info.setMinPrice(product.getMinPrice() != null ? product.getMinPrice().toString() : null);
        info.setCurrencyCode(product.getCurrencyCode());
        info.setFolderId(product.getFolderId());
        info.setSize(product.getSize());
        info.setCreatedAt(product.getCreatedAt() != null ? product.getCreatedAt().toString() : null);
        info.setUpdatedAt(product.getUpdatedAt() != null ? product.getUpdatedAt().toString() : null);
        return info;
    }
}
