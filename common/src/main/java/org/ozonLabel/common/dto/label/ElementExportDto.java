package org.ozonLabel.common.dto.label;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO для экспорта элемента этикетки
 * Содержит все данные элемента для полного экспорта/импорта
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ElementExportDto {

    // Обязательные поля
    private String id;
    private String name;
    private String type; // barcode, text, image, datamatrix, date, line, circle, square, triangle, rhombus
    private Integer layerId;
    private BigDecimal x;
    private BigDecimal y;
    private BigDecimal width;
    private BigDecimal height;
    private Integer zIndex;

    // Опциональные общие
    private BigDecimal rotation;
    private Boolean visible;
    private Boolean locked;

    // Для TEXT
    private String content;
    private TextStyleDto style;

    // Для IMAGE
    private String imageUrl;
    private Long imageId;
    private String imageName;

    // Для BARCODE
    private String barcodeType; // Code 128, EAN-13, etc.

    // Для DATE
    private String dateType; // manufacture, bestBefore, shelfLife
    private String dateFormat;
    private Boolean useCurrentDate;
    private DateSettingsDto dateSettings;

    // Для FIGURES (line, circle, square, triangle, rhombus)
    private String fillColor;
    private String borderColor;
    private Integer borderWidth;

    // Для DATAMATRIX
    private Long dataMatrixFileId;  // ID выбранного файла с кодами DataMatrix
    private String dataMatrixValue; // Значение (пусто, код берётся из файла при печати)
}
