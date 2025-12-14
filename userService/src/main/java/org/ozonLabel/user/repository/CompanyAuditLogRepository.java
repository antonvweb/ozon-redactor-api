package org.ozonLabel.user.repository;

import org.ozonLabel.common.model.AuditAction;
import org.ozonLabel.user.entity.CompanyAuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface CompanyAuditLogRepository extends JpaRepository<CompanyAuditLog, Long> {

    // Получить историю действий компании с пагинацией
    Page<CompanyAuditLog> findByCompanyOwnerIdOrderByCreatedAtDesc(Long companyOwnerId, Pageable pageable);

    // Получить историю действий конкретного пользователя в компании
    Page<CompanyAuditLog> findByCompanyOwnerIdAndUserIdOrderByCreatedAtDesc(
            Long companyOwnerId, Long userId, Pageable pageable);

    // Получить историю за период
    @Query("SELECT a FROM CompanyAuditLog a WHERE a.companyOwnerId = :ownerId " +
            "AND a.createdAt BETWEEN :startDate AND :endDate " +
            "ORDER BY a.createdAt DESC")
    Page<CompanyAuditLog> findByCompanyOwnerIdAndDateRange(
            @Param("ownerId") Long companyOwnerId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            Pageable pageable);

    // Получить историю по типу действия
    Page<CompanyAuditLog> findByCompanyOwnerIdAndActionOrderByCreatedAtDesc(
            Long companyOwnerId, AuditAction action, Pageable pageable);

    // Получить последние N записей
    List<CompanyAuditLog> findTop10ByCompanyOwnerIdOrderByCreatedAtDesc(Long companyOwnerId);

    // Подсчитать количество действий пользователя
    Long countByCompanyOwnerIdAndUserId(Long companyOwnerId, Long userId);

    // Получить действия по конкретной сущности
    @Query("SELECT a FROM CompanyAuditLog a WHERE a.companyOwnerId = :ownerId " +
            "AND a.entityType = :entityType AND a.entityId = :entityId " +
            "ORDER BY a.createdAt DESC")
    List<CompanyAuditLog> findByEntityOrderByCreatedAtDesc(
            @Param("ownerId") Long companyOwnerId,
            @Param("entityType") String entityType,
            @Param("entityId") Long entityId);
}
