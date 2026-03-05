package org.ozonLabel.common.dto.ozon;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Запрос на разрешение неоднозначностей штрихкодов при загрузке заказа
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResolveOrderRequest {
    /**
     * Список разрешений для каждого неоднозначного штрихкода
     */
    private List<BarcodeResolution> resolutions;

    /**
     * Разрешение для одного штрихкода
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BarcodeResolution {
        /**
         * Штрихкод
         */
        private String barcode;

        /**
         * Количество для печати
         */
        private Integer quantity;

        /**
         * Выбранная папка
         */
        private Long folderId;
    }
}
