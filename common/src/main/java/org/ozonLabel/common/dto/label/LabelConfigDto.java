package org.ozonLabel.common.dto.label;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LabelConfigDto {

    private BigDecimal width;
    private BigDecimal height;
    private String unit; // "mm", "px"
    private List<LayerDto> layers;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LayerDto {
        private String id;
        private String name;
        private Boolean locked;
        private Boolean visible;
        private Integer zIndex;
        private List<ElementDto> elements;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ElementDto {
        private String id;
        private String type; // "text", "barcode", "qrcode", "image", "date", "background"
        private BigDecimal x;
        private BigDecimal y;
        private BigDecimal width;
        private BigDecimal height;
        private BigDecimal rotation;

        // Для текста
        private String content;
        private String fontFamily;
        private Integer fontSize;
        private String fontWeight;
        private String color;
        private String textAlign;

        // Для баркода/QR
        private String value;
        private String format; // "EAN13", "CODE128", "QR"

        // Для изображения
        private String imageUrl;
        private String imageBase64;

        // Для даты
        private String dateFormat;

        // Для фона
        private String backgroundColor;
    }
}