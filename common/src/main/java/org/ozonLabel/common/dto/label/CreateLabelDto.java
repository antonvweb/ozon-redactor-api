package org.ozonLabel.common.dto.label;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateLabelDto {
    @NotNull(message = "ID продукта обязателен")
    private Long productId;

    private String name;

    @NotNull(message = "Конфигурация этикетки обязательна")
    @Valid
    private LabelConfigDto config;
}
