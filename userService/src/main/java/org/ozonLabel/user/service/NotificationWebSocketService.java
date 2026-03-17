package org.ozonLabel.user.service;

import lombok.extern.slf4j.Slf4j;
import org.ozonLabel.common.dto.user.NotificationResponseDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@Slf4j
public class NotificationWebSocketService {

    @Autowired(required = false)
    private SimpMessagingTemplate messagingTemplate;

    public void sendNotification(String userEmail, NotificationResponseDto notification) {
        if (messagingTemplate == null) return;
        try {
            messagingTemplate.convertAndSendToUser(
                    userEmail,
                    "/queue/notifications",
                    notification
            );
            log.debug("WS notification sent to user {}: id={}", userEmail, notification.getId());
        } catch (Exception e) {
            log.warn("Failed to send WS notification to {}: {}", userEmail, e.getMessage());
        }
    }

    public void sendUnreadCount(String userEmail, long count) {
        if (messagingTemplate == null) return;
        try {
            messagingTemplate.convertAndSendToUser(
                    userEmail,
                    "/queue/notifications/unread-count",
                    Map.of("count", count)
            );
        } catch (Exception e) {
            log.warn("Failed to send unread count to {}: {}", userEmail, e.getMessage());
        }
    }
}
