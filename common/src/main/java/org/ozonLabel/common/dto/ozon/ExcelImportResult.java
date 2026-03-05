package org.ozonLabel.common.dto.ozon;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Результат импорта товаров из Excel файла.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExcelImportResult {
    /**
     * Всего строк данных в файле (без заголовков)
     */
    private Integer totalRows;

    /**
     * Успешно создано/обновлено товаров
     */
    private Integer importedCount;

    /**
     * Пропущено (пустые строки, дубликаты)
     */
    private Integer skippedCount;

    /**
     * ID созданных товаров
     */
    private List<Long> productIds;

    /**
     * Названия колонок (= имена слоёв)
     */
    private List<String> columnNames;

    /**
     * Имя созданной/обновлённой папки
     */
    private String folderName;

    /**
     * ID папки
     */
    private Long folderId;

    /**
     * Ошибки по строкам (если были)
     */
    private List<String> errors;
}
