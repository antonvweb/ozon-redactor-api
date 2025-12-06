package org.ozonLabel.ozonApi.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateFolderDto {
    private String name;
    private Long parentFolderId;
    private String color;
    private String icon;
    private Integer position;
}