package org.ozonLabel.common.dto.ozon;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Результат загрузки заказа для печати из Excel
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderUploadResult {
    /**
     * Количество найденных товаров
     */
    private Integer matchedCount;

    /**
     * Количество штрихкодов, которые не найдены
     */
    private Integer notFoundCount;

    /**
     * Список не найденных штрихкодов
     */
    private List<String> notFoundBarcodes;

    /**
     * Штрихкоды, найденные в нескольких папках (неоднозначные)
     */
    private List<AmbiguousBarcodeDto> ambiguous;
}
