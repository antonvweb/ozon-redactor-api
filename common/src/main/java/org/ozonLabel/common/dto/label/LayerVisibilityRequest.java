package org.ozonLabel.common.dto.label;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Запрос на обновление видимости слоя
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LayerVisibilityRequest {
    
    /**
     * ID слоя
     */
    private Integer layerId;
    
    /**
     * Видимость слоя
     */
    private Boolean visible;
}
