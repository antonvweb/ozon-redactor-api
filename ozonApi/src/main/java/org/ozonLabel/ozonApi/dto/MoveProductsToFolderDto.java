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
public class MoveProductsToFolderDto {
    private List<Long> productIds;
    private Long targetFolderId; // null = убрать из папки
}
