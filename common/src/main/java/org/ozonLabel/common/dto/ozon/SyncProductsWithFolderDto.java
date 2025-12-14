package org.ozonLabel.common.dto.ozon;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class SyncProductsWithFolderDto {
    private Long folderId;
    private java.util.Map<String, Object> filter;
    private String lastId;
    private Integer limit;
}
