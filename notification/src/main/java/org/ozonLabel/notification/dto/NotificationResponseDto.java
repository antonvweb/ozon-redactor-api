package org.ozonLabel.notification.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

// DTO для ответа с уведомлением
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationResponseDto {
    private Long id;
    private String type;
    private String title;
    private String message;
    private Map<String, Object> data;
    private Boolean isRead;
    private Boolean isArchived;
    private String priority;
    private String actionType;
    private String actionUrl;
    private String relatedEntityType;
    private Long relatedEntityId;
    private SenderInfo sender;
    private LocalDateTime expiresAt;
    private LocalDateTime readAt;
    private LocalDateTime createdAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SenderInfo {
        private Long id;
        private String name;
        private String email;
    }
}