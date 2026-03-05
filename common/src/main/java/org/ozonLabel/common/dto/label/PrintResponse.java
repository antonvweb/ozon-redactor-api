package org.ozonLabel.common.dto.label;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Ответ на запрос печати этикеток
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PrintResponse {

    /**
     * Сгенерированный PDF файл в виде массива байтов
     */
    private byte[] pdfData;

    /**
     * Всего страниц (этикеток) в PDF
     */
    private Integer totalLabels;

    /**
     * Сколько DataMatrix кодов было списано
     */
    private Integer dataMatrixCodesUsed;

    /**
     * Список ID продуктов, у которых не хватило DataMatrix кодов
     */
    private List<Long> productsMissingDmCodes;
}
