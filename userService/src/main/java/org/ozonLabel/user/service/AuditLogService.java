package org.ozonLabel.user.service;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ozonLabel.user.dto.AuditLogEntryDto;
import org.ozonLabel.user.dto.AuditLogResponseDto;
import org.ozonLabel.domain.model.CompanyAuditLog;
import org.ozonLabel.domain.model.User;
import org.ozonLabel.domain.repository.CompanyAuditLogRepository;
import org.ozonLabel.domain.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditLogService {

    private final CompanyAuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

    /**
     * Записать действие в лог
     */
    @Transactional
    public void logAction(Long companyOwnerId, Long userId, CompanyAuditLog.AuditAction action,
                          String entityType, Long entityId, Map<String, Object> details) {
        try {
            CompanyAuditLog logEntry = CompanyAuditLog.builder()
                    .companyOwnerId(companyOwnerId)
                    .userId(userId)
                    .action(action)
                    .entityType(entityType)
                    .entityId(entityId)
                    .details(details)
                    .ipAddress(getClientIp())
                    .userAgent(getUserAgent())
                    .build();

            auditLogRepository.save(logEntry);

            log.info("Audit log created: {} by user {} in company {}",
                    action, userId, companyOwnerId);

        } catch (Exception e) {
            log.error("Failed to create audit log", e);
        }

    }

    /**
     * Записать действие (упрощенная версия)
     */
    @Transactional
    public void logAction(Long companyOwnerId, Long userId, CompanyAuditLog.AuditAction action) {
        logAction(companyOwnerId, userId, action, null, null, null);
    }

    /**
     * Записать действие с деталями
     */
    @Transactional
    public void logAction(Long companyOwnerId, Long userId, CompanyAuditLog.AuditAction action,
                          Map<String, Object> details) {
        logAction(companyOwnerId, userId, action, null, null, details);
    }

    /**
     * Получить историю действий компании
     */
    public AuditLogResponseDto getCompanyAuditLog(String userEmail, Long companyOwnerId,
                                                  int page, int size) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Пользователь не найден"));

        // Проверка прав доступа выполняется на уровне контроллера

        Pageable pageable = PageRequest.of(page, size);
        Page<CompanyAuditLog> logPage = auditLogRepository
                .findByCompanyOwnerIdOrderByCreatedAtDesc(companyOwnerId, pageable);

        List<AuditLogEntryDto> logs = logPage.getContent().stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());

        return AuditLogResponseDto.builder()
                .logs(logs)
                .currentPage(logPage.getNumber())
                .totalPages(logPage.getTotalPages())
                .totalElements(logPage.getTotalElements())
                .build();
    }

    /**
     * Получить историю действий конкретного пользователя
     */
    public AuditLogResponseDto getUserAuditLog(String requesterEmail, Long companyOwnerId,
                                               Long targetUserId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<CompanyAuditLog> logPage = auditLogRepository
                .findByCompanyOwnerIdAndUserIdOrderByCreatedAtDesc(companyOwnerId, targetUserId, pageable);

        List<AuditLogEntryDto> logs = logPage.getContent().stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());

        return AuditLogResponseDto.builder()
                .logs(logs)
                .currentPage(logPage.getNumber())
                .totalPages(logPage.getTotalPages())
                .totalElements(logPage.getTotalElements())
                .build();
    }

    /**
     * Получить последние действия
     */
    public List<AuditLogEntryDto> getRecentActions(Long companyOwnerId) {
        List<CompanyAuditLog> recentLogs = auditLogRepository
                .findTop10ByCompanyOwnerIdOrderByCreatedAtDesc(companyOwnerId);

        return recentLogs.stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    /**
     * Получить историю по сущности
     */
    public List<AuditLogEntryDto> getEntityHistory(Long companyOwnerId, String entityType, Long entityId) {
        List<CompanyAuditLog> logs = auditLogRepository
                .findByEntityOrderByCreatedAtDesc(companyOwnerId, entityType, entityId);

        return logs.stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    private AuditLogEntryDto mapToDto(CompanyAuditLog log) {
        User user = userRepository.findById(log.getUserId()).orElse(null);

        return AuditLogEntryDto.builder()
                .id(log.getId())
                .action(log.getAction().name())
                .userName(user != null ? user.getName() : "Unknown")
                .userEmail(user != null ? user.getEmail() : "unknown")
                .entityType(log.getEntityType())
                .entityId(log.getEntityId())
                .details(log.getDetails())
                .createdAt(log.getCreatedAt())
                .build();
    }

    private String getClientIp() {
        try {
            ServletRequestAttributes attributes =
                    (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
            HttpServletRequest request = attributes.getRequest();

            String xForwardedFor = request.getHeader("X-Forwarded-For");
            if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
                return xForwardedFor.split(",")[0].trim();
            }

            return request.getRemoteAddr();
        } catch (Exception e) {
            return null;
        }
    }

    private String getUserAgent() {
        try {
            ServletRequestAttributes attributes =
                    (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
            HttpServletRequest request = attributes.getRequest();
            return request.getHeader("User-Agent");
        } catch (Exception e) {
            return null;
        }
    }
}