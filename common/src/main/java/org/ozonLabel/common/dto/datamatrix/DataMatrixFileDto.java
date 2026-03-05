package org.ozonLabel.common.dto.datamatrix;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO для представления файла с кодами DataMatrix
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataMatrixFileDto {

    /**
     * ID файла
     */
    private Long id;

    /**
     * Имя файла
     */
    private String fileName;

    /**
     * Дата загрузки
     */
    private LocalDateTime uploadedAt;

    /**
     * Количество кодов в файле
     */
    private Integer totalCodes;

    /**
     * Количество дубликатов при загрузке
     */
    private Integer duplicateCount;

    /**
     * Детали по каждому файлу-источнику дубликатов
     */
    private List<DuplicateSourceDto> duplicateSources;

    /**
     * DTO для представления источника дубликатов
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DuplicateSourceDto {
        /**
         * ID файла-источника
         */
        private Long fileId;

        /**
         * Имя файла-источника
         */
        private String sourceFileName;

        /**
         * Количество совпадений с этим файлом
         */
        private Integer count;
    }
}
