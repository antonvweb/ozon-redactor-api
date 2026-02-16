package org.ozonLabel.common.dto.labelsize;

import lombok.Data;
import lombok.Builder;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class LabelSizeResponse {

    private Long id;
    private Long companyId;
    private String name;
    private BigDecimal width;
    private BigDecimal height;
    private Boolean isDefault;
    private Boolean isSystem;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
