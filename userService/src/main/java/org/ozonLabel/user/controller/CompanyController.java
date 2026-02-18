package org.ozonLabel.user.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ozonLabel.common.dto.ApiResponse;
import org.ozonLabel.common.dto.user.*;
import org.ozonLabel.common.model.MemberRole;
import org.ozonLabel.common.service.user.AuditLogService;
import org.ozonLabel.common.service.user.CompanyService;
import org.ozonLabel.user.entity.CompanyMember;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/company")
@RequiredArgsConstructor
@Slf4j
@Validated
public class CompanyController {

    private final CompanyService companyService;
    private final AuditLogService auditLogService;

    /**
     * Отправить приглашение пользователю присоединиться к компании
     */
    @PostMapping("/invite")
    public ResponseEntity<InviteUserResponseDto> inviteUser(
            Authentication auth,
            @Valid @RequestBody InviteUserRequestDto request) {

        String ownerEmail = auth.getName();
        log.info("Отправка приглашения от {} для {}",
                ownerEmail,
                request.getEmail() != null ? request.getEmail() : request.getPhone());

        InviteUserResponseDto response = companyService.inviteUser(ownerEmail, request);
        return ResponseEntity.ok(response);
    }

    /**
     * Отменить приглашение
     */
    @DeleteMapping("/invitation/{invitationId}")
    public ResponseEntity<ApiResponse> cancelInvitation(
            @PathVariable @Positive(message = "ID приглашения должен быть положительным") Long invitationId,
            Authentication auth) {

        String ownerEmail = auth.getName();
        log.info("Отмена приглашения {} владельцем {}", invitationId, ownerEmail);

        companyService.cancelInvitation(ownerEmail, invitationId);
        return ResponseEntity.ok(ApiResponse.success("Приглашение отменено"));
    }

    /**
     * Принять приглашение по токену
     */
    @PostMapping("/accept-invitation/{token}")
    public ResponseEntity<ApiResponse> acceptInvitation(
            @PathVariable @NotBlank(message = "Токен обязателен") String token,
            Authentication auth) {

        String userEmail = auth.getName();
        // SECURITY: Don't log the actual token value
        log.info("Пользователь {} принимает приглашение", userEmail);

        companyService.acceptInvitation(token, userEmail);

        return ResponseEntity.ok(ApiResponse.success("Вы успешно присоединились к компании!"));
    }

    /**
     * Отклонить приглашение по токену
     */
    @PostMapping("/reject-invitation/{token}")
    public ResponseEntity<ApiResponse> rejectInvitation(
            @PathVariable @NotBlank(message = "Токен обязателен") String token,
            Authentication auth) {

        String userEmail = auth.getName();
        // SECURITY: Don't log the actual token value
        log.info("Пользователь {} отклоняет приглашение", userEmail);

        companyService.rejectInvitation(token, userEmail);

        return ResponseEntity.ok(ApiResponse.success("Приглашение отклонено"));
    }

    /**
     * Принять приглашение из уведомления (по invitationId)
     */
    @PostMapping("/invitation/{invitationId}/accept")
    public ResponseEntity<ApiResponse> acceptInvitationById(
            @PathVariable @Positive(message = "ID приглашения должен быть положительным") Long invitationId,
            Authentication auth) {

        String userEmail = auth.getName();
        log.info("Пользователь {} принимает приглашение по ID {}", userEmail, invitationId);

        companyService.acceptInvitationById(invitationId, userEmail);

        return ResponseEntity.ok(ApiResponse.success("Вы успешно присоединились к компании!"));
    }

    /**
     * Отклонить приглашение из уведомления (по invitationId)
     */
    @PostMapping("/invitation/{invitationId}/reject")
    public ResponseEntity<ApiResponse> rejectInvitationById(
            @PathVariable @Positive(message = "ID приглашения должен быть положительным") Long invitationId,
            Authentication auth) {

        String userEmail = auth.getName();
        log.info("Пользователь {} отклоняет приглашение по ID {}", userEmail, invitationId);

        companyService.rejectInvitationById(invitationId, userEmail);

        return ResponseEntity.ok(ApiResponse.success("Приглашение отклонено"));
    }

    /**
     * Получить список всех компаний, к которым привязан пользовател
     */
    @GetMapping("/my-companies")
    public ResponseEntity<UserCompaniesResponseDto> getMyCompanies(Authentication auth) {
        String userEmail = auth.getName();
        log.info("Получение списка компаний для пользователя {}", userEmail);

        UserCompaniesResponseDto response = companyService.getUserCompanies(userEmail);
        return ResponseEntity.ok(response);
    }

    /**
     * Получить информацию о конкретной компании и роль пользователя в ней
     */
    @GetMapping("/{companyOwnerId}/info")
    public ResponseEntity<CompanyInfoResponseDto> getCompanyInfo(
            @PathVariable @Positive(message = "ID компании должен быть положительным") Long companyOwnerId,
            Authentication auth) {

        String userEmail = auth.getName();
        log.info("Получение информации о компании {} для пользователя {}", companyOwnerId, userEmail);

        CompanyInfoResponseDto response = companyService.getCompanyInfo(userEmail, companyOwnerId);
        return ResponseEntity.ok(response);
    }

    /**
     * Изменить роль члена команды
     */
    @PatchMapping("/{companyOwnerId}/member/{memberId}/role")
    public ResponseEntity<ApiResponse> updateMemberRole(
            @PathVariable @Positive(message = "ID компании должен быть положительным") Long companyOwnerId,
            @PathVariable @Positive(message = "ID пользователя должен быть положительным") Long memberId,
            @Valid @RequestBody UpdateRoleDto dto,
            Authentication auth) {

        String adminEmail = auth.getName();
        log.info("Изменение роли пользователя {} в компании {} администратором {}",
                memberId, companyOwnerId, adminEmail);

        companyService.updateMemberRole(adminEmail, companyOwnerId, memberId, dto.getRole());
        return ResponseEntity.ok(ApiResponse.success("Роль успешно изменена"));
    }

    /**
     * Удалить члена команды
     */
    @DeleteMapping("/{companyOwnerId}/member/{memberId}")
    public ResponseEntity<ApiResponse> removeMember(
            @PathVariable @Positive(message = "ID компании должен быть положительным") Long companyOwnerId,
            @PathVariable @Positive(message = "ID пользователя должен быть положительным") Long memberId,
            Authentication auth) {

        String adminEmail = auth.getName();
        log.info("Удаление пользователя {} из компании {} администратором {}",
                memberId, companyOwnerId, adminEmail);

        companyService.removeMember(adminEmail, companyOwnerId, memberId);
        return ResponseEntity.ok(ApiResponse.success("Пользователь удален из команды"));
    }

    /**
     * Получить историю действий в компании
     */
    @GetMapping("/{companyOwnerId}/audit-log")
    public ResponseEntity<AuditLogResponseDto> getAuditLog(
            @PathVariable @Positive(message = "ID компании должен быть положительным") Long companyOwnerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            Authentication auth) {

        String userEmail = auth.getName();

        // Проверяем, что пользователь - ADMIN
        if (!companyService.hasMinimumRole(userEmail, companyOwnerId, MemberRole.ADMIN)) {
            return ResponseEntity.status(403).body(null);
        }

        log.info("Получение истории действий компании {} пользователем {}", companyOwnerId, userEmail);

        AuditLogResponseDto response = auditLogService.getCompanyAuditLog(userEmail, companyOwnerId, page, size);
        return ResponseEntity.ok(response);
    }

    /**
     * Получить историю действий конкретного пользователя
     */
    @GetMapping("/{companyOwnerId}/audit-log/user/{userId}")
    public ResponseEntity<AuditLogResponseDto> getUserAuditLog(
            @PathVariable @Positive(message = "ID компании должен быть положительным") Long companyOwnerId,
            @PathVariable @Positive(message = "ID пользователя должен быть положительным") Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            Authentication auth) {

        String userEmail = auth.getName();

        // Проверяем права
        if (!companyService.hasMinimumRole(userEmail, companyOwnerId, MemberRole.ADMIN)) {
            return ResponseEntity.status(403).body(null);
        }

        AuditLogResponseDto response = auditLogService.getUserAuditLog(userEmail, companyOwnerId, userId, page, size);
        return ResponseEntity.ok(response);
    }

    /**
     * Получить последние действия
     */
    @GetMapping("/{companyOwnerId}/audit-log/recent")
    public ResponseEntity<List<AuditLogEntryDto>> getRecentActions(
            @PathVariable @Positive(message = "ID компании должен быть положительным") Long companyOwnerId,
            Authentication auth) {

        String userEmail = auth.getName();

        // Проверяем права
        if (!companyService.hasMinimumRole(userEmail, companyOwnerId, MemberRole.ADMIN)) {
            return ResponseEntity.status(403).body(null);
        }

        List<AuditLogEntryDto> response = auditLogService.getRecentActions(companyOwnerId);
        return ResponseEntity.ok(response);
    }
}