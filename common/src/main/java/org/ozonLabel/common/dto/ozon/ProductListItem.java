package org.ozonLabel.common.dto.ozon;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductListItem {
    @JsonProperty("product_id")
    private Long productId;
    @JsonProperty("offer_id")
    private String offerId;
}