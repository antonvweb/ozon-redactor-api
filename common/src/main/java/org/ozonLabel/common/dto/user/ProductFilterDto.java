package org.ozonLabel.common.dto.user;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class ProductFilterDto {
    private Long assignedToUserId; // Фильтр по назначенному пользователю
    private Boolean unassignedOnly; // Только неназначенные
    private Boolean myProductsOnly; // Только мои товары (для текущего пользователя)
    private String search; // Поиск по названию
    private Integer page;
    private Integer size;
}
