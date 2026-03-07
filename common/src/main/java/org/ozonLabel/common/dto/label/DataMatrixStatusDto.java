package org.ozonLabel.common.dto.label;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO для статуса DataMatrix
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataMatrixStatusDto {
    private Integer remaining; // Осталось кодов
    private Integer total;     // Всего кодов
    private String status;     // "ok", "warning", "error"
}
