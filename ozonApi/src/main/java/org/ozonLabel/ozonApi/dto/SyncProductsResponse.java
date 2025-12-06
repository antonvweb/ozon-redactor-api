package org.ozonLabel.ozonApi.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SyncProductsResponse {
    private List<ProductFrontendResponse> products;
    private Integer total;
    private String message;
}
