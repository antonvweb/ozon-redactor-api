package org.ozonLabel.common.dto.ozon;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductListResult {
    private List<ProductListItem> items;
    private Integer total;
    @JsonProperty("last_id")
    private String lastId;
}

