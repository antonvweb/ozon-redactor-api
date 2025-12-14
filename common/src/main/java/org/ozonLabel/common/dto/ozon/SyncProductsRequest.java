package org.ozonLabel.common.dto.ozon;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SyncProductsRequest {
    private Map<String, Object> filter;
    @JsonProperty("last_id")
    private String lastId;
    private Integer limit;
}