package org.ozonLabel.common.dto.label;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class DuplicateElementRequest {
    @NotNull
    private String elementId;
    
    @NotNull
    private Long folderId;
}
