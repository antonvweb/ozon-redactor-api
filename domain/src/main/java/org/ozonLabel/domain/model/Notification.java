package org.ozonLabel.domain.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;

@Entity
@Table(name = "notifications")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationType type;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> data;

    @Column(name = "is_read")
    @Builder.Default
    private Boolean isRead = false;

    @Column(name = "is_archived")
    @Builder.Default
    private Boolean isArchived = false;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private NotificationPriority priority = NotificationPriority.NORMAL;

    @Column(name = "action_type")
    private String actionType;

    @Column(name = "action_url")
    private String actionUrl;

    @Column(name = "related_entity_type")
    private String relatedEntityType;

    @Column(name = "related_entity_id")
    private Long relatedEntityId;

    @Column(name = "sender_id")
    private Long senderId;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public boolean isExpired() {
        return expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
    }

    public void markAsRead() {
        this.isRead = true;
        this.readAt = LocalDateTime.now();
    }

    public enum NotificationType {
        // Приглашения
        INVITATION,              // Приглашение в компанию
        INVITATION_ACCEPTED,     // Приглашение принято
        INVITATION_REJECTED,     // Приглашение отклонено

        // Системные
        SYSTEM,                  // Системное уведомление
        SUPPORT,                 // От техподдержки
        UPDATE,                  // Обновление системы

        // Товары
        PRODUCT,                 // Уведомление о товаре

        // Компания
        COMPANY,                 // Общее уведомление о компании
        ROLE_CHANGED,            // Роль изменена
        MEMBER_REMOVED,          // Исключен из компании

        // Финансы
        PAYMENT,                 // Платеж/счет

        // Предупреждения
        ALERT                    // Важное предупреждение
    }

    public enum NotificationPriority {
        LOW,
        NORMAL,
        HIGH,
        URGENT
    }
}