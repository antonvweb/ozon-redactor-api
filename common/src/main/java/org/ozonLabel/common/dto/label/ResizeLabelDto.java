package org.ozonLabel.common.dto.label;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO для изменения размера этикетки с опциональным пропорциональным масштабированием элементов.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResizeLabelDto {

    @NotNull(message = "Новая ширина обязательна")
    @DecimalMin(value = "10", message = "Минимальная ширина 10 мм")
    @DecimalMax(value = "300", message = "Максимальная ширина 300 мм")
    private BigDecimal newWidth;

    @NotNull(message = "Новая высота обязательна")
    @DecimalMin(value = "10", message = "Минимальная высота 10 мм")
    @DecimalMax(value = "300", message = "Максимальная высота 300 мм")
    private BigDecimal newHeight;

    /**
     * true → пересчитать x, y, width, height всех элементов пропорционально
     */
    @Builder.Default
    private Boolean autoFit = false;
}
