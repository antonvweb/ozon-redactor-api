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
import org.ozonLabel.ozonApi.entity.OzonProduct;
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

    @Override
    @Transactional(readOnly = true)
    public byte[] exportLabels(String userEmail, Long companyOwnerId, ExportRequest request) {
        companyService.checkAccess(userEmail, companyOwnerId);

        // Если folderIds указаны — собираем productIds из папок
        if ((request.getProductIds() == null || request.getProductIds().isEmpty())
                && request.getFolderIds() != null && !request.getFolderIds().isEmpty()) {
            boolean withSubs = Boolean.TRUE.equals(request.getIncludeSubfolders());
            List<Long> ids = exportFolderHelper.collectProductIds(companyOwnerId, request.getFolderIds(), withSubs);
            request = request.toBuilder().productIds(ids).build();
        }

        // Если productIds всё ещё пустые — берём ВСЕ товары компании
        if (request.getProductIds() == null || request.getProductIds().isEmpty()) {
            List<Long> allIds = productRepository.findProductIdsByCompanyId(companyOwnerId);
            if (allIds.isEmpty()) {
                throw new ValidationException("У компании нет товаров для экспорта");
            }
            request = request.toBuilder().productIds(allIds).build();
            log.info("productIds не указаны, экспортируем все {} товаров компании {}", allIds.size(), companyOwnerId);
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
     */
    private byte[] generateExcelExport(String userEmail, Long companyOwnerId, ExportRequest request) {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Этикетки");

            List<LabelResponseDto> labels = labelService.getLabelsByProductIds(
                    userEmail, companyOwnerId, request.getProductIds());

            Set<String> dynamicColumns = new LinkedHashSet<>();
            for (LabelResponseDto label : labels) {
                LabelConfigDto config = label.getConfig();
                for (LayerDto layer : config.getLayers()) {
                    if ("dynamic".equals(layer.getLayerType()) && layer.getColumnName() != null) {
                        dynamicColumns.add(layer.getColumnName());
                    } else if ("dynamic".equals(layer.getLayerType()) && layer.getName() != null) {
                        dynamicColumns.add(layer.getName());
                    }
                }
            }

            Row headerRow = sheet.createRow(0);
            int colIndex = 0;

            headerRow.createCell(colIndex++).setCellValue("Штрихкод");
            headerRow.createCell(colIndex++).setCellValue("Артикул");
            headerRow.createCell(colIndex++).setCellValue("Название");
            headerRow.createCell(colIndex++).setCellValue("Количество");

            for (String colName : dynamicColumns) {
                headerRow.createCell(colIndex++).setCellValue(colName);
            }

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

            int rowNum = 1;
            for (LabelResponseDto label : labels) {
                // Защита от null конфигурации
                if (label.getConfig() == null) {
                    log.warn("Этикетка {} не имеет конфигурации, пропускаем", label.getId());
                    continue;
                }
                LabelConfigDto config = label.getConfig();
                if (config.getElements() == null) {
                    log.warn("Этикетка {} не имеет элементов, пропускаем", label.getId());
                    continue;
                }

                Row row = sheet.createRow(rowNum++);

                int cellIndex = 0;

                Map<String, String> layerValues = new HashMap<>();
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

                String barcode = getBarcodeFromConfig(config);
                String article = getArticleFromConfig(config);
                row.createCell(cellIndex++).setCellValue(barcode != null ? barcode : "");
                row.createCell(cellIndex++).setCellValue(article != null ? article : "");
                row.createCell(cellIndex++).setCellValue(label.getName() != null ? label.getName() : "");
                row.createCell(cellIndex++).setCellValue(1);

                for (String colName : dynamicColumns) {
                    row.createCell(cellIndex++).setCellValue(layerValues.getOrDefault(colName, ""));
                }
            }

            for (int i = 0; i < headerRow.getLastCellNum(); i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(baos);
            log.info("Сгенерирован Excel экспорт для {} этикеток", labels.size());
            return baos.toByteArray();

        } catch (Exception e) {
            log.error("Ошибка генерации Excel: {}", e.getMessage(), e);
            throw new ValidationException("Ошибка генерации Excel: " + e.getMessage());
        }
    }

    private LabelConfigDto parseConfig(String configJson) {
        try {
            return objectMapper.readValue(configJson, LabelConfigDto.class);
        } catch (Exception e) {
            throw new ValidationException("Ошибка парсинга конфигурации этикетки");
        }
    }

    private String getBarcodeFromConfig(LabelConfigDto config) {
        for (ElementDto element : config.getElements()) {
            if ("barcode".equals(element.getType()) && element.getContent() != null) {
                return element.getContent();
            }
        }
        return "";
    }

    private String getArticleFromConfig(LabelConfigDto config) {
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
