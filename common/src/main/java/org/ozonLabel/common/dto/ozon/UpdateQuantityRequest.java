package org.ozonLabel.common.dto.ozon;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateQuantityRequest {
    @Min(1)
    @Max(9999)
    private Integer quantity;
}
