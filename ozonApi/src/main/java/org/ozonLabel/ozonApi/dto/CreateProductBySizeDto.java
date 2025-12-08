package org.ozonLabel.ozonApi.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
public class CreateProductBySizeDto {

    @NotNull(message = "Size cannot be null")
    @Size(min = 1, max = 50, message = "Size must be between 1 and 50 characters")
    private String size;

    private Long folderId;
}