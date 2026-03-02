package org.ozonLabel.common.dto.template;

import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO для обновления шаблона этикетки
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateLabelTemplateDto {
    
    private String name;
    private BigDecimal width;
    private BigDecimal height;
    private String unit;
    private String config;
    private String previewUrl;
}
