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
