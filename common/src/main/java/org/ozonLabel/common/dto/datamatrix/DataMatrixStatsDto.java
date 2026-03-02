package org.ozonLabel.common.dto.datamatrix;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Статистика по кодам DataMatrix для продукта
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataMatrixStatsDto {
    
    /**
     * Общее количество кодов
     */
    private Long total;
    
    /**
     * Количество неиспользованных кодов (остаток)
     */
    private Long remaining;
    
    /**
     * Количество использованных кодов
     */
    private Long used;
}
