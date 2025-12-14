package org.ozonLabel.common.service.user;

import org.ozonLabel.common.dto.user.AuditLogEntryDto;
import org.ozonLabel.common.dto.user.AuditLogResponseDto;
import org.ozonLabel.common.model.AuditAction;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface AuditLogService {

    /**
     * Логирует действие пользователя в компании
     */
    void logAction(Long companyOwnerId, Long userId, AuditAction action);

    /**
     * Логирует действие с дополнительными деталями
     */
    void logAction(Long companyOwnerId, Long userId, AuditAction action, Map<String, Object> details);

    /**
     * Полное логирование действия с entityType и entityId
     */
    void logAction(Long companyOwnerId, Long userId, AuditAction action,
                   String entityType, Long entityId, Map<String, Object> details);

    /**
     * Получение логов компании с постраничным выводом
     */
    AuditLogResponseDto getCompanyAuditLog(String userEmail, Long companyOwnerId, int page, int size);

    /**
     * Получение логов конкретного пользователя
     */
    AuditLogResponseDto getUserAuditLog(String requesterEmail, Long companyOwnerId,
                                        Long targetUserId, int page, int size);

    // Получить историю действий компании за период с пагинацией
    AuditLogResponseDto getCompanyAuditLogByDateRange(String userEmail, Long companyOwnerId,
                                                      LocalDateTime startDate, LocalDateTime endDate,
                                                      int page, int size);

    // Получить историю действий компании по типу действия
    AuditLogResponseDto getCompanyAuditLogByAction(String userEmail, Long companyOwnerId,
                                                   AuditAction action, int page, int size);

    // Получить последние N записей действий компании
    List<AuditLogEntryDto> getRecentActions(Long companyOwnerId);

    // Получить количество действий конкретного пользователя
    Long countUserActions(Long companyOwnerId, Long userId);

    // Получить историю действий по конкретной сущности
    List<AuditLogEntryDto> getEntityHistory(Long companyOwnerId, String entityType, Long entityId);

}
