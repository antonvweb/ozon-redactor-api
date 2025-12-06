package org.ozonLabel.notification.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.ozonLabel.domain.model.Notification;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateNotificationDto {
    private Long userId;
    private Notification.NotificationType type;
    private String title;
    private String message;
    private Map<String, Object> data;
    private Notification.NotificationPriority priority;
    private String actionType;
    private String actionUrl;
    private String relatedEntityType;
    private Long relatedEntityId;
    private Long senderId;
    private LocalDateTime expiresAt;
}
