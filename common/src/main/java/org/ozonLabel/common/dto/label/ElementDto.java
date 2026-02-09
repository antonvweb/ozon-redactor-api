package org.ozonLabel.common.dto.label;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ElementDto {
    // Обязательные поля
    private String id;
    private String type;
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

    // Для BARCODE
    private String barcodeType;

    // Для DATE
    private String dateType;
    private String dateFormat;
    private Boolean useCurrentDate;
    private DateSettingsDto dateSettings;
}
