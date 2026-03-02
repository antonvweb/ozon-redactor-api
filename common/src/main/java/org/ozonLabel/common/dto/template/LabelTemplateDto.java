package org.ozonLabel.common.dto.template;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO для представления шаблона этикетки
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LabelTemplateDto {
    
    private Long id;
    private String name;
    private Boolean isSystem;
    private Long companyId;
    private Long userId;
    private BigDecimal width;
    private BigDecimal height;
    private String unit;
    private String config;
    private String previewUrl;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
