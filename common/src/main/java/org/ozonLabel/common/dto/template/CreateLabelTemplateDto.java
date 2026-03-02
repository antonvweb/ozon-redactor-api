package org.ozonLabel.common.dto.template;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO для создания шаблона этикетки
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateLabelTemplateDto {
    
    @NotEmpty(message = "Название шаблона обязательно")
    private String name;
    
    @NotNull(message = "Ширина обязательна")
    private BigDecimal width;
    
    @NotNull(message = "Высота обязательна")
    private BigDecimal height;
    
    @Builder.Default
    private String unit = "mm";
    
    @NotEmpty(message = "Конфигурация обязательна")
    private String config;
    
    private String previewUrl;
}
