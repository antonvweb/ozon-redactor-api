package org.ozonLabel.common.dto.label;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO для представления доступного размера этикетки.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LabelSizeDto {
    private String name;
    private BigDecimal width;
    private BigDecimal height;
}
