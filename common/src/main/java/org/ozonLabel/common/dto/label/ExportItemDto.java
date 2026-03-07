package org.ozonLabel.common.dto.label;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * DTO для экспорта одного товара с этикеткой
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExportItemDto {

    // Данные товара
    private Long productId;
    private String barcode;
    private String article;
    private String name;
    private Integer quantity;
    private String photoUrl;

    // Данные этикетки
    private List<ElementExportDto> elements;
    private Boolean defaultElements; // true если этикетка пустая/не создана

    // Данные из Excel (если есть)
    private Map<String, String> excelData;

    // Теги
    private List<String> tags;

    // DataMatrix статистика
    private DataMatrixStatusDto dataMatrixStats;
}
