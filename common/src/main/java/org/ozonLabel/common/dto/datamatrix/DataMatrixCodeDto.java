package org.ozonLabel.common.dto.datamatrix;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO для представления кода DataMatrix
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataMatrixCodeDto {
    
    private Long id;
    private String code;
    private String gtin;
    private String serial;
    private Boolean isUsed;
    private Boolean isDuplicate;
    private LocalDateTime usedAt;
    private LocalDateTime createdAt;
}
