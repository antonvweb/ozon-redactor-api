package org.ozonLabel.common.dto.label;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Запрос на печать этикеток
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PrintRequest {
    
    /**
     * Список ID продуктов для печати
     */
    private List<Long> productIds;
    
    /**
     * Количество копий для каждого продукта (productId -> количество)
     */
    private Map<Long, Integer> copies;
    
    /**
     * Тип разделителя между разными SKU
     * DARK - тёмная полоса, LIGHT - светлая полоса, NONE - без разделителя
     */
    private String separatorType;

    /**
     * URL кастомного изображения разделителя (Pro тариф)
     */
    private String customSeparatorImageUrl;
}
