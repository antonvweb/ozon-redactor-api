package org.ozonLabel.common.dto.ozon;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Информация о неоднозначном штрихкоде (найден в нескольких папках)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AmbiguousBarcodeDto {
    /**
     * Штрихкод
     */
    private String barcode;

    /**
     * Количество для печати
     */
    private Integer quantity;

    /**
     * Список папок, в которых найден этот штрихкод
     */
    private List<FolderInfoDto> folders;
}
