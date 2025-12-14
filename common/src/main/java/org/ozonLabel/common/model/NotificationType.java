package org.ozonLabel.common.model;

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