package org.ozonLabel.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogEntryDto {
    private Long id;
    private String action;
    private String userName;
    private String userEmail;
    private String entityType;
    private Long entityId;
    private Map<String, Object> details;
    private LocalDateTime createdAt;
}
