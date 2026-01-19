package org.ozonLabel.common.dto.label;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateLabelDto {

    private String name;

    @NotNull(message = "Конфигурация этикетки обязательна")
    private LabelConfigDto config;

    private BigDecimal width;
    private BigDecimal height;
}