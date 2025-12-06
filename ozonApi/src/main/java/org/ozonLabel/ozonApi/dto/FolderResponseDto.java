package org.ozonLabel.ozonApi.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FolderResponseDto {
    private Long id;
    private Long userId;
    private Long parentFolderId;
    private String name;
    private String color;
    private String icon;
    private Integer position;
    private Integer productsCount;
    private Integer subfoldersCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
