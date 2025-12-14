package org.ozonLabel.common.dto.ozon;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateProductBySizeDto {

    @NotNull(message = "Size cannot be null")
    @Size(min = 1, max = 50, message = "Size must be between 1 and 50 characters")
    private String size;

    private Long folderId;
}