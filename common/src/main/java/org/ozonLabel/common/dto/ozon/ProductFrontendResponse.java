package org.ozonLabel.common.dto.ozon;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

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
    private List<String> tags;
}
