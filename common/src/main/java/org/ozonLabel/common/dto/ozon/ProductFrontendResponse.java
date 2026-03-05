package org.ozonLabel.common.dto.ozon;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.ozonLabel.common.dto.datamatrix.DataMatrixStatsDto;

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

    // Task 9: новые поля
    private Integer printQuantity;   // количество копий для печати, default 1
    private Boolean hasLabel;        // true если у товара есть этикетка в системе
    private DataMatrixStatsDto dataMatrixStats;  // статистика DataMatrix {total, remaining, used}
}
