package org.ozonLabel.ozonApi.repository;

import org.ozonLabel.ozonApi.entity.DataMatrixCode;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DataMatrixCodeRepository extends JpaRepository<DataMatrixCode, Long> {
    
    /**
     * Найти все коды для продукта
     */
    Page<DataMatrixCode> findByProductId(Long productId, Pageable pageable);
    
    /**
     * Найти все коды для продукта и компании
     */
    Page<DataMatrixCode> findByCompanyIdAndProductId(Long companyId, Long productId, Pageable pageable);
    
    /**
     * Найти следующий неиспользованный код для продукта
     */
    @Query("SELECT d FROM DataMatrixCode d WHERE d.productId = :productId AND d.isUsed = false ORDER BY d.id ASC")
    Optional<DataMatrixCode> findFirstUnusedByProductId(@Param("productId") Long productId);
    
    /**
     * Посчитать количество неиспользованных кодов для продукта
     */
    long countByProductIdAndIsUsedFalse(Long productId);
    
    /**
     * Посчитать общее количество кодов для продукта
     */
    long countByProductId(Long productId);
    
    /**
     * Посчитать количество использованных кодов для продукта
     */
    long countByProductIdAndIsUsedTrue(Long productId);
    
    /**
     * Проверить существование кода для компании
     */
    boolean existsByCompanyIdAndCode(Long companyId, String code);
    
    /**
     * Найти код по значению для компании
     */
    Optional<DataMatrixCode> findByCompanyIdAndCode(Long companyId, String code);
    
    /**
     * Найти все коды для списка продуктов
     */
    List<DataMatrixCode> findByProductIdIn(List<Long> productIds);
    
    /**
     * Пометить код как использованный
     */
    @Modifying
    @Query("UPDATE DataMatrixCode d SET d.isUsed = true, d.usedAt = CURRENT_TIMESTAMP WHERE d.id = :id")
    void markAsUsed(@Param("id") Long id);
}
