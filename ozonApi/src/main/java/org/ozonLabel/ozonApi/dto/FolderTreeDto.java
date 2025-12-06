package org.ozonLabel.ozonApi.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FolderTreeDto {
    private Long id;
    private String name;
    private String color;
    private String icon;
    private Integer position;
    private Integer productsCount;
    @Builder.Default
    private List<FolderTreeDto> subfolders = new ArrayList<>();
}
