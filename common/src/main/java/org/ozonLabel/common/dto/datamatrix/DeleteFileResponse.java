package org.ozonLabel.common.dto.datamatrix;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Ответ при удалении файла с кодами DataMatrix
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeleteFileResponse {

    /**
     * Количество удалённых кодов
     */
    private Integer deletedCodes;

    /**
     * Количество кодов, переставших быть дубликатами
     */
    private Integer resolvedDuplicates;

    /**
     * Сообщение о результате
     */
    private String message;
}
