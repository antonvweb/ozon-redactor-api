package org.ozonLabel.common.dto.label;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LayerDto {
    private Integer id;
    private String name;
    private Boolean locked;
    private Boolean visible;
    private String layerType; // static или dynamic
    private String columnName; // для динамических слоёв
    private String elementType; // text, barcode, datamatrix, date, image
}
