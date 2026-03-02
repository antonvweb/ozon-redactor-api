package org.ozonLabel.common.dto.label;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Запрос на печать листа подбора
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PickListRequest {
    
    /**
     * Список ID продуктов для печати
     */
    private List<Long> productIds;
}
