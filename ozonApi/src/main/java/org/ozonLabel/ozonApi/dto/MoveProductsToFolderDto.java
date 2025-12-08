package org.ozonLabel.ozonApi.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
public class MoveProductsToFolderDto {

    @NotNull(message = "Product IDs list cannot be null")
    @Size(min = 1, max = 500, message = "You can move between 1 and 500 products at once")
    private List<Long> productIds;

    // Can be null to move to root
    private Long targetFolderId;
}