package org.ozonLabel.common.model;

public enum AuditAction {
    // Действия с приглашениями
    INVITE_SENT,
    INVITATION_ACCEPTED,
    INVITATION_CANCELLED,
    INVITATION_EXPIRED,

    // Действия с членами команды
    MEMBER_JOINED,
    MEMBER_REMOVED,
    ROLE_CHANGED,

    // Действия с товарами
    PRODUCTS_SYNCED,
    PRODUCT_ASSIGNED,
    PRODUCT_UNASSIGNED,
    PRODUCT_UPDATED,
    PRODUCT_DELETED,

    // Действия с компанией
    COMPANY_INFO_UPDATED,
    COMPANY_SETTINGS_CHANGED,

    // Другие действия
    LOGIN,
    LOGOUT,
    ACCESS_DENIED
}
