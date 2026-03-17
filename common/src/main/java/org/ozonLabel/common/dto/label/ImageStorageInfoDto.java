package org.ozonLabel.common.dto.label;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageStorageInfoDto {
    private long usedBytes;
    private long maxBytes;
    private long availableBytes;
    private long imageCount;
    private long maxFileSize;
}
