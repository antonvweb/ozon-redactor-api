package org.ozonLabel.common.dto.label;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DuplicateElementResponse {
    private Integer updatedCount;
    private Integer skippedCount;
    private String message;
}
