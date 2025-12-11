package org.ozonLabel.user.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ozonLabel.common.exception.AccessDeniedException;
import org.ozonLabel.common.exception.ResourceNotFoundException;
import org.ozonLabel.common.exception.ValidationException;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    @Transactional
    public void assignProduct(String adminEmail, Long companyOwnerId, AssignProductDto dto) {
        User admin = getUserByEmail(adminEmail);

        if (!companyService.hasMinimumRole(adminEmail, companyOwnerId, CompanyMember.MemberRole.MODERATOR)) {
            throw new AccessDeniedException("Недостаточные права доступа");
        }

        OzonProduct product = productRepository.findById(dto.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product"));

        if (!product.getUserId().equals(companyOwnerId)) {
            throw new AccessDeniedException("Данный продукт не принадлежит этой компании.");
        }

        if (dto.getUserId() != null) {
            validateUserIsMember(dto.getUserId(), companyOwnerId);
        }

        Long oldAssignedUserId = product.getAssignedToUserId();
        product.setAssignedToUserId(dto.getUserId());
        productRepository.save(product);

        Map<String, Object> details = new HashMap<>();
        details.put("productId", product.getProductId());
        details.put("productName", product.getName());
        details.put("oldAssignedUserId", oldAssignedUserId);
        details.put("newAssignedUserId", dto.getUserId());

        CompanyAuditLog.AuditAction action = dto.getUserId() != null
                ? CompanyAuditLog.AuditAction.PRODUCT_ASSIGNED
                : CompanyAuditLog.AuditAction.PRODUCT_UNASSIGNED;

        auditLogService.logAction(companyOwnerId, admin.getId(), action, "PRODUCT", product.getId(), details);

        log.info("Product {} {} user {} in company {}",
                product.getId(),
                dto.getUserId() != null ? "assigned to" : "unassigned from",
                dto.getUserId(),
                companyOwnerId);
    }

    /**
     * Bulk assign products with proper transaction handling
     */
    @Transactional
    public int bulkAssignProducts(String adminEmail, Long companyOwnerId, BulkAssignProductsDto dto) {
        User admin = getUserByEmail(adminEmail);

        if (!companyService.hasMinimumRole(adminEmail, companyOwnerId, CompanyMember.MemberRole.MODERATOR)) {
            throw new AccessDeniedException("Недостаточные права доступа");
        }

        if (dto.getUserId() != null) {
            validateUserIsMember(dto.getUserId(), companyOwnerId);
        }

        // Fetch all products in one query
        List<OzonProduct> products = productRepository.findAllById(dto.getProductIds());

        // Validate all products belong to company
        List<OzonProduct> validProducts = products.stream()
                .filter(p -> p.getUserId().equals(companyOwnerId))
                .toList();

        if (validProducts.isEmpty()) {
            throw new ValidationException("Действительные продукты не найдены");
        }

        // Use batch update instead of individual saves
        int updatedCount = productRepository.bulkAssignProducts(
                dto.getProductIds(),
                companyOwnerId,
                dto.getUserId()
        );

        Map<String, Object> details = new HashMap<>();
        details.put("productIds", dto.getProductIds());
        details.put("assignedUserId", dto.getUserId());
        details.put("updatedCount", updatedCount);

        auditLogService.logAction(companyOwnerId, admin.getId(),
                CompanyAuditLog.AuditAction.PRODUCT_ASSIGNED, details);

        log.info("Bulk assignment: {} products updated in company {}", updatedCount, companyOwnerId);

        return updatedCount;
    }

    public Page<OzonProduct> getFilteredProducts(String userEmail, Long companyOwnerId,
                                                 Long assignedToUserId, Boolean unassignedOnly,
                                                 Boolean myProductsOnly, String search,
                                                 int page, int size) {
        User user = getUserByEmail(userEmail);
        companyService.checkAccess(userEmail, companyOwnerId);

        Pageable pageable = PageRequest.of(page, size);

        if (myProductsOnly != null && myProductsOnly) {
            assignedToUserId = user.getId();
        }

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

    public List<OzonProduct> getMyProducts(String userEmail, Long companyOwnerId) {
        User user = getUserByEmail(userEmail);
        companyService.checkAccess(userEmail, companyOwnerId);

        return productRepository.findByUserIdAndAssignedToUserId(companyOwnerId, user.getId());
    }

    public List<OzonProduct> getUnassignedProducts(String userEmail, Long companyOwnerId) {
        companyService.checkAccess(userEmail, companyOwnerId);
        return productRepository.findByUserIdAndAssignedToUserIdIsNull(companyOwnerId);
    }

    private void validateUserIsMember(Long userId, Long companyOwnerId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User"));

        boolean isMember = companyService.hasMinimumRole(
                user.getEmail(),
                companyOwnerId,
                CompanyMember.MemberRole.VIEWER
        );

        if (!isMember) {
            throw new ValidationException("Пользователь не является членом команды.");
        }
    }

    private User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User"));
    }
}