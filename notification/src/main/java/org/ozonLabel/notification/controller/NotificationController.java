package org.ozonLabel.notification.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ozonLabel.common.dto.ApiResponse;
import org.ozonLabel.notification.dto.*;
import org.ozonLabel.notification.service.NotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Slf4j
public class NotificationController {

    private final NotificationService notificationService;

    /**
     * Получить все уведомления пользователя
     */
    @GetMapping
    public ResponseEntity<NotificationListResponseDto> getNotifications(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Boolean unreadOnly,
            @RequestParam(required = false) String type,
            Authentication auth) {

        String userEmail = auth.getName();
        log.info("Получение уведомлений для пользователя {}", userEmail);

        NotificationListResponseDto response = notificationService.getUserNotifications(
                userEmail, page, size, unreadOnly, type);

        return ResponseEntity.ok(response);
    }

    /**
     * Получить только непрочитанные уведомления
     */
    @GetMapping("/unread")
    public ResponseEntity<List<NotificationResponseDto>> getUnreadNotifications(Authentication auth) {
        String userEmail = auth.getName();
        List<NotificationResponseDto> notifications = notificationService.getUnreadNotifications(userEmail);
        return ResponseEntity.ok(notifications);
    }

    /**
     * Получить количество непрочитанных
     */
    @GetMapping("/unread/count")
    public ResponseEntity<Map<String, Long>> getUnreadCount(Authentication auth) {
        String userEmail = auth.getName();
        Long count = notificationService.getUnreadCount(userEmail);
        return ResponseEntity.ok(Map.of("count", count));
    }

    /**
     * Получить статистику уведомлений
     */
    @GetMapping("/stats")
    public ResponseEntity<NotificationStatsDto> getStats(Authentication auth) {
        String userEmail = auth.getName();
        NotificationStatsDto stats = notificationService.getNotificationStats(userEmail);
        return ResponseEntity.ok(stats);
    }

    /**
     * Отметить уведомление как прочитанное
     */
    @PatchMapping("/{notificationId}/read")
    public ResponseEntity<ApiResponse> markAsRead(
            @PathVariable Long notificationId,
            Authentication auth) {

        String userEmail = auth.getName();
        log.info("Отметка уведомления {} как прочитанного пользователем {}", notificationId, userEmail);

        notificationService.markAsRead(userEmail, notificationId);
        return ResponseEntity.ok(ApiResponse.success("Уведомление отмечено как прочитанное"));
    }

    /**
     * Массово отметить как прочитанные
     */
    @PatchMapping("/read-multiple")
    public ResponseEntity<ApiResponse> markMultipleAsRead(
            @RequestBody BulkNotificationActionDto dto,
            Authentication auth) {

        String userEmail = auth.getName();
        int count = notificationService.markMultipleAsRead(userEmail, dto.getNotificationIds());

        return ResponseEntity.ok(ApiResponse.success(
                String.format("Отмечено %d уведомлений как прочитанные", count)));
    }

    /**
     * Отметить все как прочитанные
     */
    @PatchMapping("/read-all")
    public ResponseEntity<ApiResponse> markAllAsRead(Authentication auth) {
        String userEmail = auth.getName();
        int count = notificationService.markAllAsRead(userEmail);

        return ResponseEntity.ok(ApiResponse.success(
                String.format("Отмечено %d уведомлений как прочитанн", count)));
    }

    /**
     * Удалить уведомление
     */
    @DeleteMapping("/{notificationId}")
    public ResponseEntity<ApiResponse> deleteNotification(
            @PathVariable Long notificationId,
            Authentication auth) {

        String userEmail = auth.getName();
        log.info("Удаление уведомления {} пользователем {}", notificationId, userEmail);

        notificationService.deleteNotification(userEmail, notificationId);
        return ResponseEntity.ok(ApiResponse.success("Уведомление удалено"));
    }

    /**
     * Массово удалить уведомления
     */
    @DeleteMapping("/delete-multiple")
    public ResponseEntity<ApiResponse> deleteMultiple(
            @RequestBody BulkNotificationActionDto dto,
            Authentication auth) {

        String userEmail = auth.getName();
        int count = notificationService.deleteMultiple(userEmail, dto.getNotificationIds());

        return ResponseEntity.ok(ApiResponse.success(
                String.format("Удалено %d уведомлений", count)));
    }
}
