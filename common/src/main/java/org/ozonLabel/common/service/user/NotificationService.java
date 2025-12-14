package org.ozonLabel.common.service.user;

import org.ozonLabel.common.dto.user.CreateNotificationDto;
import org.ozonLabel.common.dto.user.NotificationListResponseDto;
import org.ozonLabel.common.dto.user.NotificationResponseDto;
import org.ozonLabel.common.dto.user.NotificationStatsDto;
import org.ozonLabel.common.model.MemberRole;
import org.ozonLabel.common.model.NotificationPriority;
import org.ozonLabel.common.model.NotificationType;

import java.time.LocalDateTime;
import java.util.List;

public interface NotificationService {

    /**
     * Создание произвольного уведомления
     */
    NotificationResponseDto createNotification(CreateNotificationDto dto);

    /**
     * Создание уведомления о приглашении
     */
    void createInvitationNotification(Long recipientId, Long invitationId,
                                      Long senderId, String senderName,
                                      String companyName, String role);

    /**
     * Создание уведомления о принятии приглашения
     */
    void createInvitationAcceptedNotification(Long ownerId, String memberName,
                                              Long memberId, String companyName);

    /**
     * Создание уведомления об отклонении приглашения
     */
    void createInvitationRejectedNotification(Long ownerId, String memberName,
                                              String companyName);

    /**
     * Создание системного уведомления
     */
    NotificationResponseDto createSystemNotification(Long userId, String title, String message);

    /**
     * Получение уведомлений пользователя с пагинацией
     */
    NotificationListResponseDto getUserNotifications(String userEmail, int page, int size,
                                                     Boolean unreadOnly, String type);

    /**
     * Получение непрочитанных уведомлений с лимитом
     */
    List<NotificationResponseDto> getUnreadNotifications(String userEmail);

    /**
     * Отметить одно уведомление как прочитанное
     */
    void markAsRead(String userEmail, Long notificationId);

    /**
     * Отметить несколько уведомлений как прочитанные
     */
    int markMultipleAsRead(String userEmail, List<Long> notificationIds);

    /**
     * Отметить все уведомления пользователя как прочитанные
     */
    int markAllAsRead(String userEmail);

    /**
     * Удалить одно уведомление
     */
    void deleteNotification(String userEmail, Long notificationId);

    /**
     * Удалить несколько уведомлений
     */
    int deleteMultiple(String userEmail, List<Long> notificationIds);

    /**
     * Получить количество непрочитанных уведомлений
     */
    Long getUnreadCount(String userEmail);

    /**
     * Получить статистику уведомлений пользователя
     */
    NotificationStatsDto getNotificationStats(String userEmail);

    /**
     * Очистка просроченных уведомлений
     */
    int cleanupExpiredNotifications();

    List<NotificationResponseDto> findByUserIdOrderByCreatedAtDesc(Long userId, int page, int size);

    List<NotificationResponseDto> findByUserIdAndIsReadFalseOrderByCreatedAtDesc(Long userId);

    List<NotificationResponseDto> findByUserIdAndTypeOrderByCreatedAtDesc(Long userId, NotificationType type, int page, int size);

    Long countByUserIdAndIsReadFalse(Long userId);

    Long countByUserIdAndType(Long userId, NotificationType type);

    List<NotificationResponseDto> findByUserIdAndRelatedEntityTypeAndRelatedEntityId(Long userId, String entityType, Long entityId);

    int markAsRead(List<Long> ids, Long userId, LocalDateTime readAt);

    int markAllAsReadForUser(Long userId, LocalDateTime readAt);

    int deleteByIds(List<Long> ids, Long userId);

    int archiveOldNotifications(Long userId, LocalDateTime before);

    int deleteExpiredNotifications(LocalDateTime now);

    int deleteExpiredNotificationsBatch(LocalDateTime now, int batchSize);

    List<NotificationResponseDto> findByUserIdAndPriorityOrderByCreatedAtDesc(Long userId, NotificationPriority priority);

}
