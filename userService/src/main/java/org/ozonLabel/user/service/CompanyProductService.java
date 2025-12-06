package org.ozonLabel.user.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ozonLabel.domain.model.OzonProduct;
import org.ozonLabel.domain.repository.OzonProductRepository;
import org.ozonLabel.user.dto.AssignProductDto;
import org.ozonLabel.user.dto.BulkAssignProductsDto;
import org.ozonLabel.domain.model.CompanyAuditLog;
import org.ozonLabel.domain.model.CompanyMember;
import org.ozonLabel.domain.model.User;
import org.ozonLabel.domain.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class CompanyProductService {

    private final OzonProductRepository productRepository;
    private final UserRepository userRepository;
    private final CompanyService companyService;
    private final AuditLogService auditLogService;

    /**
     * Назначить товар пользователю
     */
    @Transactional
    public void assignProduct(String adminEmail, Long companyOwnerId, AssignProductDto dto) {
        User admin = userRepository.findByEmail(adminEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Пользователь не найден"));

        // Проверяем права (минимум MODERATOR)
        if (!companyService.hasMinimumRole(adminEmail, companyOwnerId, CompanyMember.MemberRole.MODERATOR)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Недостаточно прав");
        }

        OzonProduct product = productRepository.findById(dto.getProductId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Товар не найден"));

        // Проверяем, что товар принадлежит компании
        if (!product.getUserId().equals(companyOwnerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Товар не принадлежит этой компании");
        }

        // Если назначаем пользователю, проверяем что он член команды
        if (dto.getUserId() != null) {
            boolean isMember = companyService.hasMinimumRole(
                    userRepository.findById(dto.getUserId())
                            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Пользователь не найден"))
                            .getEmail(),
                    companyOwnerId,
                    CompanyMember.MemberRole.VIEWER
            );

            if (!isMember) {
                throw new IllegalArgumentException("Пользователь не является членом команды");
            }
        }

        Long oldAssignedUserId = product.getAssignedToUserId();
        product.setAssignedToUserId(dto.getUserId());
        productRepository.save(product);

        // Логируем действие
        Map<String, Object> details = new HashMap<>();
        details.put("productId", product.getProductId());
        details.put("productName", product.getName());
        details.put("oldAssignedUserId", oldAssignedUserId);
        details.put("newAssignedUserId", dto.getUserId());

        CompanyAuditLog.AuditAction action = dto.getUserId() != null
                ? CompanyAuditLog.AuditAction.PRODUCT_ASSIGNED
                : CompanyAuditLog.AuditAction.PRODUCT_UNASSIGNED;

        auditLogService.logAction(companyOwnerId, admin.getId(), action, "PRODUCT", product.getId(), details);

        log.info("Товар {} {} пользователю {} в компании {}",
                product.getId(),
                dto.getUserId() != null ? "назначен" : "снят с назначения",
                dto.getUserId(),
                companyOwnerId);
    }

    /**
     * Массовое назначение товаров
     */
    @Transactional
    public void bulkAssignProducts(String adminEmail, Long companyOwnerId, BulkAssignProductsDto dto) {
        User admin = userRepository.findByEmail(adminEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Пользователь не найден"));

        // Проверяем права
        if (!companyService.hasMinimumRole(adminEmail, companyOwnerId, CompanyMember.MemberRole.MODERATOR)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Недостаточно прав");
        }

        // Если назначаем пользователю, проверяем что он член команды
        if (dto.getUserId() != null) {
            boolean isMember = companyService.hasMinimumRole(
                    userRepository.findById(dto.getUserId())
                            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Пользователь не найден"))
                            .getEmail(),
                    companyOwnerId,
                    CompanyMember.MemberRole.VIEWER
            );

            if (!isMember) {
                throw new IllegalArgumentException("Пользователь не является членом команды");
            }
        }

        int updatedCount = 0;
        for (Long productId : dto.getProductIds()) {
            try {
                OzonProduct product = productRepository.findById(productId).orElse(null);
                if (product != null && product.getUserId().equals(companyOwnerId)) {
                    product.setAssignedToUserId(dto.getUserId());
                    productRepository.save(product);
                    updatedCount++;
                }
            } catch (Exception e) {
                log.error("Ошибка при назначении товара {}", productId, e);
            }
        }

        // Логируем действие
        Map<String, Object> details = new HashMap<>();
        details.put("productIds", dto.getProductIds());
        details.put("assignedUserId", dto.getUserId());
        details.put("updatedCount", updatedCount);

        auditLogService.logAction(companyOwnerId, admin.getId(),
                CompanyAuditLog.AuditAction.PRODUCT_ASSIGNED, details);

        log.info("Массовое назначение: {} товаров обновлено в компании {}", updatedCount, companyOwnerId);
    }

    /**
     * Получить товары по фильтру
     */
    public Page<OzonProduct> getFilteredProducts(String userEmail, Long companyOwnerId,
                                                 Long assignedToUserId, Boolean unassignedOnly,
                                                 Boolean myProductsOnly, String search,
                                                 int page, int size) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Пользователь не найден"));

        // Проверяем доступ к компании
        companyService.checkAccess(userEmail, companyOwnerId);

        Pageable pageable = PageRequest.of(page, size);

        // Если myProductsOnly = true, показываем только товары текущего пользователя
        if (myProductsOnly != null && myProductsOnly) {
            assignedToUserId = user.getId();
        }

        // Фильтруем товары
        if (unassignedOnly != null && unassignedOnly) {
            return productRepository.findByUserIdAndAssignedToUserIdIsNull(companyOwnerId, pageable);
        } else if (assignedToUserId != null) {
            if (search != null && !search.trim().isEmpty()) {
                return productRepository.findByUserIdAndAssignedToUserIdAndNameContainingIgnoreCase(
                        companyOwnerId, assignedToUserId, search, pageable);
            } else {
                return productRepository.findByUserIdAndAssignedToUserId(companyOwnerId, assignedToUserId, pageable);
            }
        } else if (search != null && !search.trim().isEmpty()) {
            return productRepository.findByUserIdAndNameContainingIgnoreCase(companyOwnerId, search, pageable);
        } else {
            return productRepository.findByUserIdOrderByUpdatedAtDesc(companyOwnerId, pageable);
        }
    }

    /**
     * Получить мои товары (назначенные текущему пользователю)
     */
    public List<OzonProduct> getMyProducts(String userEmail, Long companyOwnerId) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Пользователь не найден"));

        // Проверяем доступ
        companyService.checkAccess(userEmail, companyOwnerId);

        return productRepository.findByUserIdAndAssignedToUserId(companyOwnerId, user.getId());
    }

    /**
     * Получить неназначенные товары
     */
    public List<OzonProduct> getUnassignedProducts(String userEmail, Long companyOwnerId) {
        // Проверяем доступ
        companyService.checkAccess(userEmail, companyOwnerId);

        return productRepository.findByUserIdAndAssignedToUserIdIsNull(companyOwnerId);
    }
}
