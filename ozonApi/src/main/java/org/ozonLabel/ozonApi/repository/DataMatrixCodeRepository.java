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
     * Найти следующий неиспользованный код из конкретного файла
     */
    @Query("SELECT d FROM DataMatrixCode d WHERE d.fileId = :fileId AND d.isUsed = false ORDER BY d.id ASC")
    Optional<DataMatrixCode> findFirstUnusedByFileId(@Param("fileId") Long fileId);

    /**
     * Найти следующий неиспользованный код из конкретного файла для компании
     */
    @Query("SELECT d FROM DataMatrixCode d WHERE d.fileId = :fileId AND d.companyId = :companyId AND d.isUsed = false ORDER BY d.id ASC")
    Optional<DataMatrixCode> findFirstUnusedByFileIdAndCompanyId(@Param("fileId") Long fileId, @Param("companyId") Long companyId);
    
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
     * Посчитать количество неиспользованных кодов для файла
     */
    long countByFileIdAndIsUsedFalse(Long fileId);

    /**
     * Посчитать общее количество кодов для файла
     */
    long countByFileId(Long fileId);

    /**
     * Посчитать количество использованных кодов для файла
     */
    long countByFileIdAndIsUsedTrue(Long fileId);
    
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

    /**
     * Найти все коды для файла
     */
    List<DataMatrixCode> findByFileId(Long fileId);

    /**
     * Удалить все коды для файла
     */
    void deleteAllByFileId(Long fileId);

    /**
     * Найти все дубликаты, у которых код встречается только 1 раз в пуле компании
     * (для пересчёта после удаления файла)
     */
    @Query("SELECT d.code FROM DataMatrixCode d WHERE d.companyId = :companyId AND d.isDuplicate = true " +
           "GROUP BY d.code HAVING COUNT(d.id) = 1")
    List<String> findOrphanDuplicateCodes(@Param("companyId") Long companyId);

    /**
     * Снять флаг дубликата с кодов
     */
    @Modifying
    @Query("UPDATE DataMatrixCode d SET d.isDuplicate = false WHERE d.companyId = :companyId AND d.code IN :codes")
    void clearDuplicateFlag(@Param("companyId") Long companyId, @Param("codes") List<String> codes);
    
    /**
     * Получить статистику DataMatrix кодов по productId
     * Возвращает список Object[] где каждый элемент: [productId, total, remaining]
     */
    @Query("SELECT d.productId, COUNT(d.id) as total, " +
           "SUM(CASE WHEN d.isUsed = false THEN 1 ELSE 0 END) as remaining " +
           "FROM DataMatrixCode d WHERE d.companyId = :companyId AND d.productId IN :productIds " +
           "GROUP BY d.productId")
    List<Object[]> getStatsByProductIds(@Param("companyId") Long companyId, @Param("productIds") List<Long> productIds);
}
