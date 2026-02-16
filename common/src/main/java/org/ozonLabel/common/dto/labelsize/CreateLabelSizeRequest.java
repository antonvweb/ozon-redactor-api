package org.ozonLabel.common.dto.labelsize;

import lombok.Data;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;

@Data
public class CreateLabelSizeRequest {

    @NotBlank(message = "Название обязательно")
    @Size(max = 255, message = "Название не должно превышать 255 символов")
    private String name;

    @NotNull(message = "Ширина обязательна")
    @DecimalMin(value = "1.0", message = "Ширина должна быть не менее 1 мм")
    @DecimalMax(value = "500.0", message = "Ширина не должна превышать 500 мм")
    private BigDecimal width;

    @NotNull(message = "Высота обязательна")
    @DecimalMin(value = "1.0", message = "Высота должна быть не менее 1 мм")
    @DecimalMax(value = "500.0", message = "Высота не должна превышать 500 мм")
    private BigDecimal height;

    private Boolean isDefault = false;
}
