package org.ozonLabel.common.dto.label;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DateSettingsDto {
    private DateFieldsDto fields;
    private Boolean useCurrentDate;
    private String format;
    private String customDateText;

    // Умная дата
    private Boolean smartDate;

    // Срок годности
    private Integer shelfLifeValue;
    private String shelfLifeUnit; // "day" | "month" | "year"

    // Что показывать
    private Boolean showManufactureDate;
    private Boolean showBestBefore;
    private Boolean showShelfLife;

    // Пользовательская дата (если не умная дата)
    private String customDate;

    // Отображение
    private Boolean abbreviateText;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DateFieldsDto {
        private Boolean manufacture;
        private Boolean bestBefore;
        private Boolean shelfLife;
    }
}
