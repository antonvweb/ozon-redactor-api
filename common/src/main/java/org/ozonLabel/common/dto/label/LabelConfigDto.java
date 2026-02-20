package org.ozonLabel.common.dto.label;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
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
    @NotNull(message = "Ширина обязательна")
    private BigDecimal width;

    @NotNull(message = "Высота обязательна")
    private BigDecimal height;

    @Builder.Default
    private String unit = "mm";

    @NotEmpty(message = "Должен быть хотя бы один слой")
    @Valid
    private List<LayerDto> layers;

    @NotNull(message = "Список элементов обязателен")
    @Valid
    private List<ElementDto> elements;

}
