package org.ozonLabel.common.dto.ozon;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FolderDataMatrixStats {
    /**
     * Общее количество DataMatrix кодов в папке
     */
    private Long totalCodes;

    /**
     * Количество оставшихся (неиспользованных) кодов
     */
    private Long remainingCodes;

    /**
     * Количество товаров с DataMatrix кодами
     */
    private Integer productsWithCodes;

    /**
     * Количество товаров без DataMatrix кодов
     */
    private Integer productsWithoutCodes;
}
