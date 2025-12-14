package org.ozonLabel.common.dto.user;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.ozonLabel.common.model.NotificationPriority;
import org.ozonLabel.common.model.NotificationType;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateNotificationDto {
    private Long userId;
    private NotificationType type;
    private String title;
    private String message;
    private Map<String, Object> data;
    private NotificationPriority priority;
    private String actionType;
    private String actionUrl;
    private String relatedEntityType;
    private Long relatedEntityId;
    private Long senderId;
    private LocalDateTime expiresAt;
}
