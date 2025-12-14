package org.ozonLabel.common.dto.ozon;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductInfoResponse {
    private List<ProductInfo> items;
}
