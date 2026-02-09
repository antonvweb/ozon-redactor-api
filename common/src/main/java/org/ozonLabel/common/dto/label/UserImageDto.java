package org.ozonLabel.common.dto.label;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserImageDto {
    private Long id;
    private String originalName;
    private String url;
    private String mimeType;
    private Long sizeBytes;
    private LocalDateTime createdAt;
}
