package org.ozonLabel.ozonApi.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class MoveFolderDto {
    private Long targetParentFolderId; // null = переместить в корень
}
