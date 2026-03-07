package org.ozonLabel.ozonApi.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.ozonLabel.common.dto.label.*;
import org.ozonLabel.common.exception.user.ValidationException;
import org.ozonLabel.common.service.label.ExportService;
import org.ozonLabel.common.service.label.LabelService;
import org.ozonLabel.common.service.label.PrintService;
import org.ozonLabel.common.service.user.CompanyService;
import org.ozonLabel.ozonApi.entity.Label;
import org.ozonLabel.ozonApi.entity.OzonProduct;
import org.ozonLabel.ozonApi.repository.LabelRepository;
import org.ozonLabel.ozonApi.repository.OzonProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.io.ByteArrayInputStream;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExportServiceImpl implements ExportService {

    private final LabelService labelService;
    private final PrintService printService;
    private final CompanyService companyService;
    private final ObjectMapper objectMapper;
    private final ExportFolderHelper exportFolderHelper;
    private final OzonProductRepository productRepository;
    private final LabelRepository labelRepository;

    @Override
    @Transactional(readOnly = true)
    public byte[] exportLabels(String userEmail, Long companyOwnerId, ExportRequest request) {
        companyService.checkAccess(userEmail, companyOwnerId);

        // Проверка: если ничего не выбрано — возвращаем ошибку
        boolean noProductIds = request.getProductIds() == null || request.getProductIds().isEmpty();
        boolean noFolderIds = request.getFolderIds() == null || request.getFolderIds().isEmpty();

        if (noProductIds && noFolderIds) {
            throw new ValidationException("Требуется выбрать товары или папки для экспорта");
        }

        // Если folderIds указаны — собираем productIds из папок
        if (noProductIds && !noFolderIds) {
            boolean withSubs = Boolean.TRUE.equals(request.getIncludeSubfolders());
            List<Long> ids = exportFolderHelper.collectProductIds(companyOwnerId, request.getFolderIds(), withSubs);
            if (ids.isEmpty()) {
                throw new ValidationException("В выбранных папках нет товаров для экспорта");
            }
            request = request.toBuilder().productIds(ids).build();
            log.info("Экспорт из папок: {} товаров", ids.size());
        }

        // Финальная проверка
        if (request.getProductIds() == null || request.getProductIds().isEmpty()) {
            throw new ValidationException("Не выбрано товаров для экспорта");
        }

        String format = request.getFormat() != null ? request.getFormat().toUpperCase() : "EXCEL";
        String exportType = request.getExportType() != null ? request.getExportType() : "labels";

        return switch (format) {
            case "ZIP" -> generateZipExport(userEmail, companyOwnerId, request);
            case "PDF" -> generatePdfExport(userEmail, companyOwnerId, request);
            case "EXCEL" -> "database".equals(exportType)
                    ? generateExcelDatabase(userEmail, companyOwnerId, request)
                    : generateExcelExport(userEmail, companyOwnerId, request);
            default -> throw new ValidationException("Неподдерживаемый формат: " + format);
        };
    }

    /**
     * Генерация Excel файла с этикетками
     * Экспортирует все данные этикетки (элементы: штрихкоды, текст, изображения, DataMatrix)
     * Если этикетка не создана — экспортирует данные из товара по умолчанию
     */
    private byte[] generateExcelExport(String userEmail, Long companyOwnerId, ExportRequest request) {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Этикетки");

            // Получаем все товары и их этикетки
            List<Long> productIds = request.getProductIds();
            List<OzonProduct> products = productRepository.findAllById(productIds);
            
            // Получаем сохранённые этикетки
            List<Label> labels = labelRepository.findByCompanyIdAndProductIdIn(companyOwnerId, productIds);
            Map<Long, Label> labelByProductId = new HashMap<>();
            for (Label label : labels) {
                labelByProductId.put(label.getProductId(), label);
            }

            log.info("Экспорт этикеток: {} товаров, {} сохранённых этикеток", products.size(), labels.size());

            // Собираем динамические колонки из всех этикеток
            Set<String> dynamicColumns = new LinkedHashSet<>();
            for (Label label : labels) {
                if (label.getConfig() != null) {
                    LabelConfigDto config = parseConfig(label.getConfig());
                    if (config != null && config.getLayers() != null) {
                        for (LayerDto layer : config.getLayers()) {
                            if ("dynamic".equals(layer.getLayerType()) && layer.getColumnName() != null) {
                                dynamicColumns.add(layer.getColumnName());
                            } else if ("dynamic".equals(layer.getLayerType()) && layer.getName() != null) {
                                dynamicColumns.add(layer.getName());
                            }
                        }
                    }
                }
            }

            // Создаём заголовки
            Row headerRow = sheet.createRow(0);
            int colIndex = 0;

            headerRow.createCell(colIndex++).setCellValue("Штрихкод");
            headerRow.createCell(colIndex++).setCellValue("Артикул");
            headerRow.createCell(colIndex++).setCellValue("Название");
            headerRow.createCell(colIndex++).setCellValue("Количество");
            headerRow.createCell(colIndex++).setCellValue("Фото");

            // Добавляем колонки для динамических слоёв
            for (String colName : dynamicColumns) {
                headerRow.createCell(colIndex++).setCellValue(colName);
            }

            // Добавляем колонки для элементов этикетки
            headerRow.createCell(colIndex++).setCellValue("Элементы этикетки (JSON)");

            applyHeaderStyle(workbook, headerRow);

            // Записываем данные
            int rowNum = 1;
            for (OzonProduct product : products) {
                Row row = sheet.createRow(rowNum++);
                colIndex = 0;

                // Получаем этикетку для товара (если есть)
                Label label = labelByProductId.get(product.getProductId());
                LabelConfigDto config = null;
                boolean hasLabel = false;

                if (label != null && label.getConfig() != null) {
                    config = parseConfig(label.getConfig());
                    hasLabel = config != null && config.getElements() != null && !config.getElements().isEmpty();
                }

                // Данные из товара (по умолчанию)
                String barcode = getFirstBarcode(product);
                String article = product.getOfferId() != null ? product.getOfferId() : "";
                String name = product.getName() != null ? product.getName() : "";
                Integer quantity = product.getPrintQuantity() != null ? product.getPrintQuantity() : 1;
                String photoUrl = getFirstImage(product);

                // Если есть этикетка — используем данные из неё
                if (hasLabel) {
                    String barcodeFromConfig = getBarcodeFromConfig(config);
                    if (barcodeFromConfig != null && !barcodeFromConfig.isEmpty()) {
                        barcode = barcodeFromConfig;
                    }
                }

                // Записываем основные данные
                row.createCell(colIndex++).setCellValue(barcode != null ? barcode : "");
                row.createCell(colIndex++).setCellValue(article != null ? article : "");
                row.createCell(colIndex++).setCellValue(name != null ? name : "");
                row.createCell(colIndex++).setCellValue(quantity != null ? quantity : 0);
                row.createCell(colIndex++).setCellValue(photoUrl != null ? photoUrl : "");

                // Динамические колонки
                Map<String, String> layerValues = new HashMap<>();
                if (hasLabel) {
                    for (ElementDto element : config.getElements()) {
                        LayerDto layer = config.getLayers().stream()
                                .filter(l -> l.getId().equals(element.getLayerId()))
                                .findFirst()
                                .orElse(null);

                        if (layer != null && "dynamic".equals(layer.getLayerType())) {
                            String colName = layer.getColumnName() != null ? layer.getColumnName() : layer.getName();
                            if (colName != null && element.getContent() != null) {
                                layerValues.put(colName, element.getContent());
                            }
                        }
                    }
                }

                for (String colName : dynamicColumns) {
                    row.createCell(colIndex++).setCellValue(layerValues.getOrDefault(colName, ""));
                }

                // Экспортируем все элементы этикетки в JSON формате
                if (hasLabel) {
                    try {
                        String elementsJson = objectMapper.writeValueAsString(config.getElements());
                        row.createCell(colIndex++).setCellValue(elementsJson);
                    } catch (Exception e) {
                        log.warn("Ошибка записи элементов этикетки для продукта {}: {}", product.getProductId(), e.getMessage());
                        row.createCell(colIndex++).setCellValue("");
                    }
                } else {
                    // Этикетка не создана — помечаем как данные по умолчанию
                    row.createCell(colIndex++).setCellValue("{\"default\": true, \"productId\": " + product.getProductId() + "}");
                }
            }

            // Автонастройка ширины колонок
            for (int i = 0; i < headerRow.getLastCellNum(); i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(baos);
            log.info("Сгенерирован Excel экспорт для {} товаров", products.size());
            return baos.toByteArray();

        } catch (Exception e) {
            log.error("Ошибка генерации Excel: {}", e.getMessage(), e);
            throw new ValidationException("Ошибка генерации Excel: " + e.getMessage());
        }
    }

    private LabelConfigDto parseConfig(String configJson) {
        if (configJson == null || configJson.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.readValue(configJson, LabelConfigDto.class);
        } catch (Exception e) {
            log.warn("Ошибка парсинга конфигурации этикетки: {}", e.getMessage());
            return null;
        }
    }

    private String getBarcodeFromConfig(LabelConfigDto config) {
        if (config == null || config.getElements() == null) {
            return "";
        }
        for (ElementDto element : config.getElements()) {
            if ("barcode".equals(element.getType()) && element.getContent() != null) {
                return element.getContent();
            }
        }
        return "";
    }

    private String getArticleFromConfig(LabelConfigDto config) {
        if (config == null || config.getElements() == null) {
            return "";
        }
        for (ElementDto element : config.getElements()) {
            if ("text".equals(element.getType()) && element.getContent() != null) {
                String content = element.getContent();
                if (content.contains("Артикул") || content.contains("арт.")) {
                    return content;
                }
            }
        }
        return "";
    }

    /**
     * Генерация ZIP архива с PDF файлами этикеток
     */
    private byte[] generateZipExport(String userEmail, Long companyOwnerId, ExportRequest request) {
        List<Long> productIds = request.getProductIds();
        if (productIds == null || productIds.isEmpty()) {
            throw new ValidationException("Не указаны продукты для экспорта");
        }

        String fileNaming = request.getFileNaming() != null ? request.getFileNaming() : "barcode";

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ZipOutputStream zos = new ZipOutputStream(baos)) {

            for (Long productId : productIds) {
                // Получить продукт для именования файла
                OzonProduct product = productRepository.findByUserIdAndProductId(companyOwnerId, productId)
                        .orElseThrow(() -> new ValidationException("Продукт не найден: " + productId));

                // Получить этикетку
                LabelResponseDto label = labelService.getLabelByProductId(userEmail, companyOwnerId, productId);

                // Сгенерировать PDF одной страницы
                PrintRequest printRequest = PrintRequest.builder()
                        .productIds(Collections.singletonList(productId))
                        .copies(new HashMap<>())
                        .separatorType("NONE")
                        .build();
                PrintResponse printResponse = printService.generateLabelsPdf(userEmail, companyOwnerId, printRequest);
                byte[] pdfData = printResponse.getPdfData();

                // Получить имя файла
                String fileName = exportFolderHelper.getFileName(product, fileNaming) + ".pdf";

                // Добавить в ZIP
                ZipEntry zipEntry = new ZipEntry(fileName);
                zos.putNextEntry(zipEntry);
                zos.write(pdfData);
                zos.closeEntry();

                log.debug("Добавлен файл в ZIP: {}", fileName);
            }

            log.info("Сгенерирован ZIP экспорт для {} этикеток", productIds.size());
            return baos.toByteArray();

        } catch (ValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Ошибка генерации ZIP: {}", e.getMessage(), e);
            throw new ValidationException("Ошибка генерации ZIP: " + e.getMessage());
        }
    }

    /**
     * Генерация PDF экспорта (один файл со всеми этикетками)
     */
    private byte[] generatePdfExport(String userEmail, Long companyOwnerId, ExportRequest request) {
        List<Long> productIds = request.getProductIds();
        if (productIds == null || productIds.isEmpty()) {
            throw new ValidationException("Не указаны продукты для экспорта");
        }

        PrintRequest printRequest = PrintRequest.builder()
                .productIds(productIds)
                .copies(new HashMap<>())
                .separatorType("NONE")
                .build();
        PrintResponse printResponse = printService.generateLabelsPdf(userEmail, companyOwnerId, printRequest);
        log.info("Сгенерирован PDF экспорт для {} этикеток", productIds.size());
        return printResponse.getPdfData();
    }

    /**
     * Генерация Excel экспорта базы данных
     */
    private byte[] generateExcelDatabase(String userEmail, Long companyOwnerId, ExportRequest request) {
        List<Long> productIds = request.getProductIds();
        if (productIds == null || productIds.isEmpty()) {
            throw new ValidationException("Не указаны продукты для экспорта");
        }

        Boolean separateFiles = Boolean.TRUE.equals(request.getSeparateFiles());
        Boolean includePhotos = Boolean.TRUE.equals(request.getIncludePhotos());

        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            if (separateFiles) {
                // Отдельный лист для каждой папки
                createDatabaseSheetsByFolder(workbook, userEmail, companyOwnerId, productIds, includePhotos);
            } else {
                // Один лист со всеми продуктами
                createDatabaseSingleSheet(workbook, companyOwnerId, productIds, includePhotos);
            }

            workbook.write(baos);
            log.info("Сгенерирован Excel database экспорт для {} продуктов", productIds.size());
            return baos.toByteArray();

        } catch (Exception e) {
            log.error("Ошибка генерации Excel database: {}", e.getMessage(), e);
            throw new ValidationException("Ошибка генерации Excel database: " + e.getMessage());
        }
    }

    /**
     * Создать один лист со всеми продуктами
     */
    private void createDatabaseSingleSheet(Workbook workbook, Long companyOwnerId, List<Long> productIds,
                                           boolean includePhotos) {
        Sheet sheet = workbook.createSheet("Продукты");

        // Заголовки
        Row headerRow = sheet.createRow(0);
        int colIndex = 0;
        headerRow.createCell(colIndex++).setCellValue("ID");
        headerRow.createCell(colIndex++).setCellValue("Штрихкод");
        headerRow.createCell(colIndex++).setCellValue("Артикул");
        headerRow.createCell(colIndex++).setCellValue("Название");
        if (includePhotos) {
            headerRow.createCell(colIndex++).setCellValue("Фото");
        }

        applyHeaderStyle(workbook, headerRow);

        // Данные
        int rowNum = 1;
        for (Long productId : productIds) {
            OzonProduct product = productRepository.findByUserIdAndProductId(companyOwnerId, productId)
                    .orElse(null);
            if (product == null) {
                continue;
            }

            Row row = sheet.createRow(rowNum++);
            colIndex = 0;
            row.createCell(colIndex++).setCellValue(product.getProductId());
            row.createCell(colIndex++).setCellValue(getFirstBarcode(product));
            row.createCell(colIndex++).setCellValue(product.getOfferId() != null ? product.getOfferId() : "");
            row.createCell(colIndex++).setCellValue(product.getName() != null ? product.getName() : "");
            if (includePhotos) {
                row.createCell(colIndex++).setCellValue(getFirstImage(product));
            }
        }

        autoSizeColumns(sheet, includePhotos ? 5 : 4);
    }

    /**
     * Создать отдельные листы по папкам
     */
    private void createDatabaseSheetsByFolder(Workbook workbook, String userEmail, Long companyOwnerId,
                                               List<Long> productIds, boolean includePhotos) {
        // Получить все продукты с их папками
        Map<Long, List<OzonProduct>> productsByFolder = new HashMap<>();

        for (Long productId : productIds) {
            OzonProduct product = productRepository.findByUserIdAndProductId(companyOwnerId, productId)
                    .orElse(null);
            if (product == null) {
                continue;
            }

            Long folderId = product.getFolderId();
            productsByFolder.computeIfAbsent(folderId, k -> new ArrayList<>()).add(product);
        }

        // Создать лист для каждой папки
        for (Map.Entry<Long, List<OzonProduct>> entry : productsByFolder.entrySet()) {
            Long folderId = entry.getKey();
            List<OzonProduct> products = entry.getValue();

            String sheetName = folderId != null ? getFolderName(userEmail, companyOwnerId, folderId) : "Без папки";
            // Excel ограничивает имя листа 31 символом
            if (sheetName.length() > 31) {
                sheetName = sheetName.substring(0, 31);
            }

            Sheet sheet = workbook.createSheet(sheetName);

            // Заголовки
            Row headerRow = sheet.createRow(0);
            int colIndex = 0;
            headerRow.createCell(colIndex++).setCellValue("ID");
            headerRow.createCell(colIndex++).setCellValue("Штрихкод");
            headerRow.createCell(colIndex++).setCellValue("Артикул");
            headerRow.createCell(colIndex++).setCellValue("Название");
            if (includePhotos) {
                headerRow.createCell(colIndex++).setCellValue("Фото");
            }

            applyHeaderStyle(workbook, headerRow);

            // Данные
            int rowNum = 1;
            for (OzonProduct product : products) {
                Row row = sheet.createRow(rowNum++);
                colIndex = 0;
                row.createCell(colIndex++).setCellValue(product.getProductId());
                row.createCell(colIndex++).setCellValue(getFirstBarcode(product));
                row.createCell(colIndex++).setCellValue(product.getOfferId() != null ? product.getOfferId() : "");
                row.createCell(colIndex++).setCellValue(product.getName() != null ? product.getName() : "");
                if (includePhotos) {
                    row.createCell(colIndex++).setCellValue(getFirstImage(product));
                }
            }

            autoSizeColumns(sheet, includePhotos ? 5 : 4);
        }
    }

    /**
     * Получить имя папки по ID
     */
    private String getFolderName(String userEmail, Long companyOwnerId, Long folderId) {
        // Для простоты возвращаем ID, если нет доступа к сервису папок
        // В реальной реализации можно использовать FolderService
        return "Папка_" + folderId;
    }

    /**
     * Применить стиль заголовка
     */
    private void applyHeaderStyle(Workbook workbook, Row headerRow) {
        CellStyle headerStyle = workbook.createCellStyle();
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerFont.setFontHeightInPoints((short) 11);
        headerStyle.setFont(headerFont);
        headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        headerStyle.setBorderBottom(BorderStyle.THIN);
        headerStyle.setBorderTop(BorderStyle.THIN);
        headerStyle.setBorderLeft(BorderStyle.THIN);
        headerStyle.setBorderRight(BorderStyle.THIN);

        for (int i = 0; i < headerRow.getLastCellNum(); i++) {
            headerRow.getCell(i).setCellStyle(headerStyle);
        }
    }

    /**
     * Автоматически настроить ширину колонок
     */
    private void autoSizeColumns(Sheet sheet, int columnCount) {
        for (int i = 0; i < columnCount; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    /**
     * Получить первый штрихкод из продукта
     */
    private String getFirstBarcode(OzonProduct product) {
        if (product.getBarcodes() == null || product.getBarcodes().isBlank()) {
            return "";
        }
        try {
            List<String> barcodes = objectMapper.readValue(
                    product.getBarcodes(),
                    new TypeReference<List<String>>() {}
            );
            if (barcodes != null && !barcodes.isEmpty()) {
                return barcodes.get(0);
            }
        } catch (Exception e) {
            log.warn("Ошибка парсинга barcodes для продукта {}: {}", product.getProductId(), e.getMessage());
        }
        return "";
    }

    /**
     * Получить первое изображение из продукта
     */
    private String getFirstImage(OzonProduct product) {
        if (product.getImages() == null || product.getImages().isBlank()) {
            return "";
        }
        try {
            List<String> images = objectMapper.readValue(
                    product.getImages(),
                    new TypeReference<List<String>>() {}
            );
            if (images != null && !images.isEmpty()) {
                return images.get(0);
            }
        } catch (Exception e) {
            log.warn("Ошибка парсинга images для продукта {}: {}", product.getProductId(), e.getMessage());
        }
        return "";
    }
}
