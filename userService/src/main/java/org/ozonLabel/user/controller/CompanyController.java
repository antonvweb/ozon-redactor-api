package org.ozonLabel.user.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ozonLabel.common.dto.ApiResponse;
import org.ozonLabel.domain.model.CompanyMember;
import org.ozonLabel.domain.model.OzonProduct;
import org.ozonLabel.user.dto.*;
import org.ozonLabel.user.service.AuditLogService;
import org.ozonLabel.user.service.CompanyProductService;
import org.ozonLabel.user.service.CompanyService;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/company")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Slf4j
public class CompanyController {

    private final CompanyService companyService;
    private final AuditLogService auditLogService;
    private final CompanyProductService productService;

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
            @PathVariable Long invitationId,
            Authentication auth) {

        String ownerEmail = auth.getName();
        log.info("Отмена приглашения {} владельцем {}", invitationId, ownerEmail);

        companyService.cancelInvitation(ownerEmail, invitationId);
        return ResponseEntity.ok(ApiResponse.success("Приглашение отменео"));
    }

    /**
     * Принять приглашение по токену
     */
    @PostMapping("/accept-invitation/{token}")
    public ResponseEntity<ApiResponse> acceptInvitation(
            @PathVariable String token,
            Authentication auth) {

        String userEmail = auth.getName();
        log.info("Пользователь {} принимает приглашение с токеном {}", userEmail, token);

        companyService.acceptInvitation(token, userEmail);

        return ResponseEntity.ok(ApiResponse.success("Вы успешно присоединились к компании!"));
    }

    /**
     * Отклонить приглашение по токену
     */
    @PostMapping("/reject-invitation/{token}")
    public ResponseEntity<ApiResponse> rejectInvitation(
            @PathVariable String token,
            Authentication auth) {

        String userEmail = auth.getName();
        log.info("Пользователь {} отклоняет приглашение с токеном {}", userEmail, token);

        companyService.rejectInvitation(token, userEmail);

        return ResponseEntity.ok(ApiResponse.success("Приглашение отклонено"));
    }

    /**
     * Получить список всех компаний, к которым привязан пользователь
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
            @PathVariable Long companyOwnerId,
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
            @PathVariable Long companyOwnerId,
            @PathVariable Long memberId,
            @RequestBody UpdateRoleDto dto,
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
            @PathVariable Long companyOwnerId,
            @PathVariable Long memberId,
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
            @PathVariable Long companyOwnerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            Authentication auth) {

        String userEmail = auth.getName();

        // Проверяем, что пользователь - ADMIN
        if (!companyService.hasMinimumRole(userEmail, companyOwnerId, CompanyMember.MemberRole.ADMIN)) {
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
            @PathVariable Long companyOwnerId,
            @PathVariable Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            Authentication auth) {

        String userEmail = auth.getName();

        // Проверяем права
        if (!companyService.hasMinimumRole(userEmail, companyOwnerId, CompanyMember.MemberRole.ADMIN)) {
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
            @PathVariable Long companyOwnerId,
            Authentication auth) {

        String userEmail = auth.getName();

        // Проверяем права
        if (!companyService.hasMinimumRole(userEmail, companyOwnerId, CompanyMember.MemberRole.ADMIN)) {
            return ResponseEntity.status(403).body(null);
        }

        List<AuditLogEntryDto> response = auditLogService.getRecentActions(companyOwnerId);
        return ResponseEntity.ok(response);
    }

    /**
     * Назначить товар пользователю
     */
    @PostMapping("/{companyOwnerId}/product/assign")
    public ResponseEntity<ApiResponse> assignProduct(
            @PathVariable Long companyOwnerId,
            @RequestBody AssignProductDto dto,
            Authentication auth) {

        String userEmail = auth.getName();
        log.info("Назначение товара {} пользователю {} в компании {}",
                dto.getProductId(), dto.getUserId(), companyOwnerId);

        productService.assignProduct(userEmail, companyOwnerId, dto);
        return ResponseEntity.ok(ApiResponse.success("Товар успешно назначен"));
    }

    /**
     * Массовое назначение товаров
     */
    @PostMapping("/{companyOwnerId}/product/bulk-assign")
    public ResponseEntity<ApiResponse> bulkAssignProducts(
            @PathVariable Long companyOwnerId,
            @RequestBody BulkAssignProductsDto dto,
            Authentication auth) {

        String userEmail = auth.getName();
        log.info("Массовое назначение {} товаров пользователю {} в компании {}",
                dto.getProductIds().size(), dto.getUserId(), companyOwnerId);

        productService.bulkAssignProducts(userEmail, companyOwnerId, dto);
        return ResponseEntity.ok(ApiResponse.success("Товары успешно назначены"));
    }

    /**
     * Получить отфильтрованные товары
     */
    @GetMapping("/{companyOwnerId}/products")
    public ResponseEntity<Map<String, Object>> getFilteredProducts(
            @PathVariable Long companyOwnerId,
            @RequestParam(required = false) Long assignedToUserId,
            @RequestParam(required = false) Boolean unassignedOnly,
            @RequestParam(required = false) Boolean myProductsOnly,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication auth) {

        String userEmail = auth.getName();

        Page<OzonProduct> products = productService.getFilteredProducts(
                userEmail, companyOwnerId, assignedToUserId, unassignedOnly,
                myProductsOnly, search, page, size);

        Map<String, Object> response = new HashMap<>();
        response.put("products", products.getContent());
        response.put("currentPage", products.getNumber());
        response.put("totalPages", products.getTotalPages());
        response.put("totalElements", products.getTotalElements());

        return ResponseEntity.ok(response);
    }

    /**
     * Получить мои товары
     */
    @GetMapping("/{companyOwnerId}/products/my")
    public ResponseEntity<List<OzonProduct>> getMyProducts(
            @PathVariable Long companyOwnerId,
            Authentication auth) {

        String userEmail = auth.getName();
        List<OzonProduct> products = productService.getMyProducts(userEmail, companyOwnerId);
        return ResponseEntity.ok(products);
    }

    /**
     * Получить неназначенные товары
     */
    @GetMapping("/{companyOwnerId}/products/unassigned")
    public ResponseEntity<List<OzonProduct>> getUnassignedProducts(
            @PathVariable Long companyOwnerId,
            Authentication auth) {

        String userEmail = auth.getName();
        List<OzonProduct> products = productService.getUnassignedProducts(userEmail, companyOwnerId);
        return ResponseEntity.ok(products);
    }
}