package org.ozonLabel.ozonApi.service;

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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExportServiceImpl implements ExportService {

    private final LabelService labelService;
    private final PrintService printService;
    private final CompanyService companyService;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(readOnly = true)
    public byte[] exportLabels(String userEmail, Long companyOwnerId, ExportRequest request) {
        companyService.checkAccess(userEmail, companyOwnerId);

        String format = request.getFormat() != null ? request.getFormat().toUpperCase() : "EXCEL";

        if ("PDF".equals(format)) {
            // Переиспользуем генерацию PDF
            PrintRequest printRequest = PrintRequest.builder()
                    .productIds(request.getProductIds())
                    .copies(new HashMap<>())
                    .separatorType("NONE")
                    .build();
            return printService.generateLabelsPdf(userEmail, companyOwnerId, printRequest);
        } else if ("EXCEL".equals(format)) {
            return generateExcelExport(userEmail, companyOwnerId, request);
        } else {
            throw new ValidationException("Неподдерживаемый формат экспорта: " + format);
        }
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
                LabelConfigDto config = label.getConfig();
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

                row.createCell(cellIndex++).setCellValue(getBarcodeFromConfig(config));
                row.createCell(cellIndex++).setCellValue(getArticleFromConfig(config));
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
}
