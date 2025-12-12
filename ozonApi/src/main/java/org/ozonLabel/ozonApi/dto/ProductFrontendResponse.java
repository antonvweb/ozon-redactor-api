package org.ozonLabel.ozonApi.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductFrontendResponse {
    private String image;
    private String name;
    private String id;  // Changed to String for frontend consistency
    private String price;  // Changed to String
    private Long sku;
    private String offerId;
    private Integer modelCount;
    private List<String> statuses;  // Changed to List<String> assuming it's an array
    private String colorIndex;

    // New fields for frontend consistency
    private String barcode;
    private String ozonArticle;
    private String sellerArticle;
    private Integer stock;
    private String color;
    @Builder.Default
    private List<String> tags = new ArrayList<>();
}
