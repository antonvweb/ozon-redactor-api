package org.ozonLabel.common.dto.ozon;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateFolderDto {

    @NotNull(message = "Folder name cannot be null")
    @Size(min = 1, max = 100, message = "Folder name must be between 1 and 100 characters")
    private String name;

    private Long parentFolderId;
    private String color;
    private String icon;
}