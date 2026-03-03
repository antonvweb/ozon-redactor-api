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
public class TextStyleDto {
    private String fontFamily;
    private BigDecimal fontSize;
    private String fontWeight;
    private Boolean italic;
    private Boolean underline;
    private String textAlign;
    private String color;
    private String backgroundColor;
    private Boolean inverted;
    private BigDecimal lineHeight;
    private BigDecimal letterSpacing;
}
