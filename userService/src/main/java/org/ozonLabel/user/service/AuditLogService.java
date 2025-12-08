package org.ozonLabel.user.service;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ozonLabel.common.exception.ResourceNotFoundException;
import org.ozonLabel.user.dto.AuditLogEntryDto;
import org.ozonLabel.user.dto.AuditLogResponseDto;
import org.ozonLabel.domain.model.CompanyAuditLog;
import org.ozonLabel.domain.model.User;
import org.ozonLabel.domain.repository.CompanyAuditLogRepository;
import org.ozonLabel.domain.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

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
     * Log action with proper error handling
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

            log.debug("Audit log created: {} by user {} in company {}",
                    action, userId, companyOwnerId);

        } catch (Exception e) {
            // Log error but don't fail the main operation
            log.error("Failed to create audit log for action {} by user {} in company {}",
                    action, userId, companyOwnerId, e);
        }
    }

    @Transactional
    public void logAction(Long companyOwnerId, Long userId, CompanyAuditLog.AuditAction action) {
        logAction(companyOwnerId, userId, action, null, null, null);
    }

    @Transactional
    public void logAction(Long companyOwnerId, Long userId, CompanyAuditLog.AuditAction action,
                          Map<String, Object> details) {
        logAction(companyOwnerId, userId, action, null, null, details);
    }

    /**
     * Get company audit log with N+1 problem fixed
     */
    public AuditLogResponseDto getCompanyAuditLog(String userEmail, Long companyOwnerId,
                                                  int page, int size) {
        userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User"));

        Pageable pageable = PageRequest.of(page, size);
        Page<CompanyAuditLog> logPage = auditLogRepository
                .findByCompanyOwnerIdOrderByCreatedAtDesc(companyOwnerId, pageable);

        // Collect all user IDs
        List<Long> userIds = logPage.getContent().stream()
                .map(CompanyAuditLog::getUserId)
                .distinct()
                .collect(Collectors.toList());

        // Fetch all users in one query
        Map<Long, User> userMap = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        List<AuditLogEntryDto> logs = logPage.getContent().stream()
                .map(log -> mapToDto(log, userMap))
                .collect(Collectors.toList());

        return AuditLogResponseDto.builder()
                .logs(logs)
                .currentPage(logPage.getNumber())
                .totalPages(logPage.getTotalPages())
                .totalElements(logPage.getTotalElements())
                .build();
    }

    /**
     * Get user audit log with N+1 problem fixed
     */
    public AuditLogResponseDto getUserAuditLog(String requesterEmail, Long companyOwnerId,
                                               Long targetUserId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<CompanyAuditLog> logPage = auditLogRepository
                .findByCompanyOwnerIdAndUserIdOrderByCreatedAtDesc(companyOwnerId, targetUserId, pageable);

        User targetUser = userRepository.findById(targetUserId).orElse(null);
        Map<Long, User> userMap = targetUser != null
                ? Map.of(targetUserId, targetUser)
                : Map.of();

        List<AuditLogEntryDto> logs = logPage.getContent().stream()
                .map(log -> mapToDto(log, userMap))
                .collect(Collectors.toList());

        return AuditLogResponseDto.builder()
                .logs(logs)
                .currentPage(logPage.getNumber())
                .totalPages(logPage.getTotalPages())
                .totalElements(logPage.getTotalElements())
                .build();
    }

    public List<AuditLogEntryDto> getRecentActions(Long companyOwnerId) {
        List<CompanyAuditLog> recentLogs = auditLogRepository
                .findTop10ByCompanyOwnerIdOrderByCreatedAtDesc(companyOwnerId);

        // Fetch users in batch
        List<Long> userIds = recentLogs.stream()
                .map(CompanyAuditLog::getUserId)
                .distinct()
                .collect(Collectors.toList());

        Map<Long, User> userMap = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        return recentLogs.stream()
                .map(log -> mapToDto(log, userMap))
                .collect(Collectors.toList());
    }

    public List<AuditLogEntryDto> getEntityHistory(Long companyOwnerId, String entityType, Long entityId) {
        List<CompanyAuditLog> logs = auditLogRepository
                .findByEntityOrderByCreatedAtDesc(companyOwnerId, entityType, entityId);

        // Fetch users in batch
        List<Long> userIds = logs.stream()
                .map(CompanyAuditLog::getUserId)
                .distinct()
                .collect(Collectors.toList());

        Map<Long, User> userMap = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        return logs.stream()
                .map(log -> mapToDto(log, userMap))
                .collect(Collectors.toList());
    }

    private AuditLogEntryDto mapToDto(CompanyAuditLog log, Map<Long, User> userMap) {
        User user = userMap.get(log.getUserId());

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