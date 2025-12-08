package org.ozonLabel.ozonApi.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
public class CreateFolderDto {

    @NotNull(message = "Folder name cannot be null")
    @Size(min = 1, max = 100, message = "Folder name must be between 1 and 100 characters")
    private String name;

    private Long parentFolderId;
    private String color;
    private String icon;
}