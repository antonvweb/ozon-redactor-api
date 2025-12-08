package org.ozonLabel.user.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AssignProductDto {

    @NotNull(message = "Product ID cannot be null")
    private Long productId;

    // userId can be null to unassign
    private Long userId;
}