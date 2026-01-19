package org.ozonLabel.common.dto.label;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LabelResponseDto {

    private Long id;
    private Long userId;
    private Long productId;
    private String name;
    private LabelConfigDto config;
    private BigDecimal width;
    private BigDecimal height;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}