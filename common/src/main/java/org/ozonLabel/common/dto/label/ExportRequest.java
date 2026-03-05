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
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class ExportRequest {

    /**
     * Список ID продуктов для экспорта
     */
    private List<Long> productIds;

    /**
     * Формат экспорта: EXCEL, PDF или ZIP
     */
    private String format;

    /**
     * Включать ли слои в экспорт
     */
    private Boolean includeLayers;

    /**
     * Список ID папок для экспорта (если productIds не задан)
     */
    private List<Long> folderIds;

    /**
     * Включать ли подпапки при экспорте папок
     */
    private Boolean includeSubfolders;

    /**
     * Тип экспорта: labels (этикетки) или database (база данных)
     */
    private String exportType;

    /**
     * Стратегия именования файлов: barcode (по штрихкоду) или article (по артикулу)
     */
    private String fileNaming;

    /**
     * Включать ли фотографии в экспорт базы данных
     */
    private Boolean includePhotos;

    /**
     * Создавать ли отдельные файлы в ZIP (true) или один файл (false)
     */
    private Boolean separateFiles;
}
