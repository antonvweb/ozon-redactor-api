package org.ozonLabel.user.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class BulkAssignProductsDto {

    @NotNull(message = "Product IDs list cannot be null")
    @Size(min = 1, max = 500, message = "You can assign between 1 and 500 products at once")
    private List<Long> productIds;

    // userId can be null to unassign
    private Long userId;
}