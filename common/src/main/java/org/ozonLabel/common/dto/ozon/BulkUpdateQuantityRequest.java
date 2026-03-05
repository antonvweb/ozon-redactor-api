package org.ozonLabel.common.dto.ozon;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkUpdateQuantityRequest {
    private List<Long> productIds;  // productId (не id)
    
    @Min(1)
    @Max(9999)
    private Integer quantity;
}
