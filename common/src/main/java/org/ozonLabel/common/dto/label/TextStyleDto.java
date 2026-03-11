package org.ozonLabel.common.dto.label;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
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
    
    @JsonDeserialize(using = FontSizeDeserializer.class)
    private BigDecimal fontSize;
    
    private String fontWeight;
    private Boolean italic;
    private Boolean underline;
    private String textAlign;
    private String color;
    private BigDecimal lineHeight;
    private BigDecimal letterSpacing;
}
