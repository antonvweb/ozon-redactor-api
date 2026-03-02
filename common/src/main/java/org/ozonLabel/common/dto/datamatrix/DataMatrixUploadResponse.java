package org.ozonLabel.common.dto.datamatrix;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Ответ при загрузке кодов DataMatrix
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataMatrixUploadResponse {
    
    /**
     * Общее количество кодов в файле
     */
    private Integer total;
    
    /**
     * Количество новых (не дубликатов) кодов
     */
    private Integer newCodes;
    
    /**
     * Количество дубликатов
     */
    private Integer duplicates;
    
    /**
     * Список загруженных кодов
     */
    private List<String> codes;
}
