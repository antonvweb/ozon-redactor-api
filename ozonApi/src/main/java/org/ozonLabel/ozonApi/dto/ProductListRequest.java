package org.ozonLabel.ozonApi.dto;

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
public class ProductListRequest {
    private Map<String, Object> filter;
    @JsonProperty("last_id")
    private String lastId;
    private Integer limit;
}