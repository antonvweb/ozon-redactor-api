package org.ozonLabel.user.repository;

import org.ozonLabel.common.model.NotificationPriority;
import org.ozonLabel.common.model.NotificationType;
import org.ozonLabel.user.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    // Получить все уведомления пользователя
    Page<Notification> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    // Получить непрочитанные уведомления
    Page<Notification> findByUserIdAndIsReadFalseOrderByCreatedAtDesc(Long userId, Pageable pageable);

    List<Notification> findByUserIdAndIsReadFalseOrderByCreatedAtDesc(Long userId);

    // Получить по типу
    Page<Notification> findByUserIdAndTypeOrderByCreatedAtDesc(
            Long userId, NotificationType type, Pageable pageable);

    // Подсчитать непрочитанные
    Long countByUserIdAndIsReadFalse(Long userId);

    // Подсчитать по типу
    Long countByUserIdAndType(Long userId, NotificationType type);

    // Найти по связанной сущности
    List<Notification> findByUserIdAndRelatedEntityTypeAndRelatedEntityId(
            Long userId, String entityType, Long entityId);

    // Массово отметить как прочитанные
    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true, n.readAt = :readAt WHERE n.id IN :ids AND n.userId = :userId")
    int markAsRead(@Param("ids") List<Long> ids, @Param("userId") Long userId, @Param("readAt") LocalDateTime readAt);

    // ⭐ NEW: Отметить ВСЕ уведомления пользователя как прочитанные (bulk update без предварительной выборки)
    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true, n.readAt = :readAt WHERE n.userId = :userId AND n.isRead = false")
    int markAllAsReadForUser(@Param("userId") Long userId, @Param("readAt") LocalDateTime readAt);

    // Массово удалить
    @Modifying
    @Query("DELETE FROM Notification n WHERE n.id IN :ids AND n.userId = :userId")
    int deleteByIds(@Param("ids") List<Long> ids, @Param("userId") Long userId);

    // Архивировать старые прочитанные уведомления
    @Modifying
    @Query("UPDATE Notification n SET n.isArchived = true WHERE n.userId = :userId AND n.isRead = true AND n.createdAt < :before")
    int archiveOldNotifications(@Param("userId") Long userId, @Param("before") LocalDateTime before);

    // Удалить истекшие уведомления
    @Modifying
    @Query("DELETE FROM Notification n WHERE n.expiresAt IS NOT NULL AND n.expiresAt < :now")
    int deleteExpiredNotifications(@Param("now") LocalDateTime now);

    // ⭐ NEW: Удалить истекшие уведомления пакетами (batch deletion для избежания блокировок)
    @Modifying
    @Query(value = "DELETE FROM notifications WHERE expires_at IS NOT NULL AND expires_at < :now LIMIT :batchSize",
            nativeQuery = true)
    int deleteExpiredNotificationsBatch(@Param("now") LocalDateTime now, @Param("batchSize") int batchSize);

    // Получить уведомления с приоритетом
    List<Notification> findByUserIdAndPriorityOrderByCreatedAtDesc(
            Long userId, NotificationPriority priority);
}