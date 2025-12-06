package org.ozonLabel.ozonApi.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductFrontendResponse {
    private String image;
    private String name;
    private Long id;
    private BigDecimal price;
    private Long sku;
    @JsonProperty("offer_id")
    private String offerId;
    @JsonProperty("model_count")
    private Integer modelCount;
    private Map<String, Object> statuses;
    @JsonProperty("color_index")
    private String colorIndex;
}
