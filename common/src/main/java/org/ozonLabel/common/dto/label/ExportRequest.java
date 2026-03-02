package org.ozonLabel.common.dto.label;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Запрос на экспорт этикеток
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExportRequest {
    
    /**
     * Список ID продуктов для экспорта
     */
    private List<Long> productIds;
    
    /**
     * Формат экспорта: EXCEL или PDF
     */
    private String format;
    
    /**
     * Включать ли слои в экспорт
     */
    private Boolean includeLayers;
}
