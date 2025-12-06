package org.ozonLabel.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkAssignProductsDto {
    private List<Long> productIds;
    private Long userId; // null = снять назначение со всех
}
