package org.ozonLabel.ozonApi.repository;

import org.ozonLabel.ozonApi.entity.OzonProduct;
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
public interface OzonProductRepository extends JpaRepository<OzonProduct, Long> {
    boolean existsByUserIdAndProductId(Long userId, Long productId);


    Optional<OzonProduct> findByUserIdAndProductId(Long userId, Long productId);
    List<OzonProduct> findByUserId(Long userId);

    @Query("SELECT p FROM OzonProduct p WHERE p.userId = :userId ORDER BY p.updatedAt DESC")
    List<OzonProduct> findByUserIdOrderByUpdatedAtDesc(@Param("userId") Long userId);

    Page<OzonProduct> findByUserIdOrderByUpdatedAtDesc(Long userId, Pageable pageable);

    void deleteByUserId(Long userId);

    Long countByUserId(Long userId);

    List<OzonProduct> findByUserIdAndAssignedToUserId(Long userId, Long assignedToUserId);

    Page<OzonProduct> findByUserIdAndAssignedToUserId(Long userId, Long assignedToUserId, Pageable pageable);

    List<OzonProduct> findByUserIdAndAssignedToUserIdIsNull(Long userId);

    Page<OzonProduct> findByUserIdAndAssignedToUserIdIsNull(Long userId, Pageable pageable);

    Page<OzonProduct> findByUserIdAndNameContainingIgnoreCase(Long userId, String name, Pageable pageable);

    Page<OzonProduct> findByUserIdAndAssignedToUserIdAndNameContainingIgnoreCase(
            Long userId, Long assignedToUserId, String name, Pageable pageable);

    Long countByUserIdAndAssignedToUserId(Long userId, Long assignedToUserId);

    Long countByUserIdAndAssignedToUserIdIsNull(Long userId);

    // ⭐ NEW: Bulk update for product assignment
    @Modifying
    @Query("UPDATE OzonProduct p SET p.assignedToUserId = :assignedUserId " +
            "WHERE p.id IN :productIds AND p.userId = :companyOwnerId")
    int bulkAssignProducts(@Param("productIds") List<Long> productIds,
                           @Param("companyOwnerId") Long companyOwnerId,
                           @Param("assignedUserId") Long assignedUserId);

    // ⭐ NEW: Bulk move products to folder
    // Измените в OzonProductRepository.java:
    @Modifying
    @Query("UPDATE OzonProduct p SET p.folderId = :folderId " +
            "WHERE p.productId IN :productIds AND p.userId = :userId") // ⬅️ измените p.id на p.productId
    int bulkMoveProductsToFolder(@Param("productIds") List<Long> productIds,
                                 @Param("userId") Long userId,
                                 @Param("folderId") Long folderId);

    // Folder-related methods
    List<OzonProduct> findByUserIdAndFolderId(Long userId, Long folderId);

    Page<OzonProduct> findByUserIdAndFolderId(Long userId, Long folderId, Pageable pageable);

    List<OzonProduct> findByUserIdAndFolderIdIsNull(Long userId);

    Page<OzonProduct> findByUserIdAndFolderIdIsNull(Long userId, Pageable pageable);

    Long countByUserIdAndFolderId(Long userId, Long folderId);

    Long countByUserIdAndFolderIdIsNull(Long userId);

    // Size-related methods
    List<OzonProduct> findByUserIdAndSize(Long userId, String size);

    // Поиск по названию, тегу, артикулам с пагинацией
    @Query(value = "SELECT * FROM ozon_products p WHERE p.user_id = :userId AND " +
            "(LOWER(p.name) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(p.offer_id) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "CAST(p.sku AS TEXT) LIKE CONCAT('%', :searchTerm, '%') OR " +
            "LOWER(CAST(p.tags AS TEXT)) LIKE LOWER(CONCAT('%', :searchTerm, '%')))",
            countQuery = "SELECT COUNT(*) FROM ozon_products p WHERE p.user_id = :userId AND " +
                    "(LOWER(p.name) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
                    "LOWER(p.offer_id) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
                    "CAST(p.sku AS TEXT) LIKE CONCAT('%', :searchTerm, '%') OR " +
                    "LOWER(CAST(p.tags AS TEXT)) LIKE LOWER(CONCAT('%', :searchTerm, '%')))",
            nativeQuery = true)
    Page<OzonProduct> searchProducts(@Param("userId") Long userId,
                                     @Param("searchTerm") String searchTerm,
                                     Pageable pageable);

    // Поиск в конкретной папке
    @Query(value = "SELECT * FROM ozon_products p WHERE p.user_id = :userId AND p.folder_id = :folderId AND " +
            "(LOWER(p.name) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(p.offer_id) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "CAST(p.sku AS TEXT) LIKE CONCAT('%', :searchTerm, '%') OR " +
            "LOWER(CAST(p.tags AS TEXT)) LIKE LOWER(CONCAT('%', :searchTerm, '%')))",
            countQuery = "SELECT COUNT(*) FROM ozon_products p WHERE p.user_id = :userId AND p.folder_id = :folderId AND " +
                    "(LOWER(p.name) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
                    "LOWER(p.offer_id) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
                    "CAST(p.sku AS TEXT) LIKE CONCAT('%', :searchTerm, '%') OR " +
                    "LOWER(CAST(p.tags AS TEXT)) LIKE LOWER(CONCAT('%', :searchTerm, '%')))",
            nativeQuery = true)
    Page<OzonProduct> searchProductsInFolder(@Param("userId") Long userId,
                                             @Param("folderId") Long folderId,
                                             @Param("searchTerm") String searchTerm,
                                             Pageable pageable);

    @Query(value = """
    SELECT * FROM ozon_products p 
    WHERE p.user_id = :userId 
    AND (
        LOWER(p.name) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR 
        LOWER(p.offer_id) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR 
        CAST(p.sku AS TEXT) LIKE CONCAT('%', :searchTerm, '%') OR 
        LOWER(CAST(p.tags AS TEXT)) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR
        LOWER(CAST(p.barcodes AS TEXT)) LIKE LOWER(CONCAT('%', :searchTerm, '%'))
    )
    ORDER BY
        CASE WHEN :sortBy = 'name' AND :sortDirection = 'ASC' THEN p.name END ASC,
        CASE WHEN :sortBy = 'name' AND :sortDirection = 'DESC' THEN p.name END DESC,
        
        CASE WHEN :sortBy = 'price' AND :sortDirection = 'ASC' THEN p.price END ASC,
        CASE WHEN :sortBy = 'price' AND :sortDirection = 'DESC' THEN p.price END DESC,
        
        CASE WHEN :sortBy = 'sku' AND :sortDirection = 'ASC' THEN p.sku END ASC,
        CASE WHEN :sortBy = 'sku' AND :sortDirection = 'DESC' THEN p.sku END DESC,
        
        CASE WHEN :sortBy = 'offerId' AND :sortDirection = 'ASC' THEN p.offer_id END ASC,
        CASE WHEN :sortBy = 'offerId' AND :sortDirection = 'DESC' THEN p.offer_id END DESC,
        
        CASE WHEN :sortBy = 'size' AND :sortDirection = 'ASC' THEN p.size END ASC,
        CASE WHEN :sortBy = 'size' AND :sortDirection = 'DESC' THEN p.size END DESC,
        
        -- Сортировка по количеству на складе (сумма remaining)
        CASE WHEN :sortBy = 'stock' AND :sortDirection = 'ASC'
                     THEN (SELECT COALESCE(SUM((s->>'remaining')::integer), 0)
                           FROM jsonb_array_elements(p.stocks::jsonb->'stocks') AS s)
        END ASC,
        CASE WHEN :sortBy = 'stock' AND :sortDirection = 'DESC' 
             THEN (SELECT COALESCE(SUM((s->>'remaining')::integer), 0) 
                   FROM jsonb_array_elements(p.stocks::jsonb->'stocks') AS s) 
        END DESC,
        
        -- Сортировка по первому баркоду
        CASE WHEN :sortBy = 'barcode' AND :sortDirection = 'ASC' 
             THEN (p.barcodes::jsonb ->> 0) 
        END ASC,
        CASE WHEN :sortBy = 'barcode' AND :sortDirection = 'DESC' 
             THEN (p.barcodes::jsonb ->> 0) 
        END DESC,
        
        -- Сортировка по первому тегу
        CASE WHEN :sortBy = 'tag' AND :sortDirection = 'ASC' 
             THEN (p.tags::jsonb ->> 0) 
        END ASC,
        CASE WHEN :sortBy = 'tag' AND :sortDirection = 'DESC' 
             THEN (p.tags::jsonb ->> 0) 
        END DESC
    """,
            countQuery = """
    SELECT COUNT(*) FROM ozon_products p 
    WHERE p.user_id = :userId 
    AND (
        LOWER(p.name) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR 
        LOWER(p.offer_id) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR 
        CAST(p.sku AS TEXT) LIKE CONCAT('%', :searchTerm, '%') OR 
        LOWER(CAST(p.tags AS TEXT)) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR
        LOWER(CAST(p.barcodes AS TEXT)) LIKE LOWER(CONCAT('%', :searchTerm, '%'))
    )
    """,
            nativeQuery = true)
    Page<OzonProduct> searchProductsWithSort(
            @Param("userId") Long userId,
            @Param("searchTerm") String searchTerm,
            @Param("sortBy") String sortBy,
            @Param("sortDirection") String sortDirection,
            Pageable pageable);

    @Query(value = """
    SELECT * FROM ozon_products p 
    WHERE p.user_id = :userId AND p.folder_id = :folderId
    AND (
        LOWER(p.name) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR 
        LOWER(p.offer_id) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR 
        CAST(p.sku AS TEXT) LIKE CONCAT('%', :searchTerm, '%') OR 
        LOWER(CAST(p.tags AS TEXT)) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR
        LOWER(CAST(p.barcodes AS TEXT)) LIKE LOWER(CONCAT('%', :searchTerm, '%'))
    )
    ORDER BY
        CASE WHEN :sortBy = 'name' AND :sortDirection = 'ASC' THEN p.name END ASC,
        CASE WHEN :sortBy = 'name' AND :sortDirection = 'DESC' THEN p.name END DESC,
        CASE WHEN :sortBy = 'price' AND :sortDirection = 'ASC' THEN p.price END ASC,
        CASE WHEN :sortBy = 'price' AND :sortDirection = 'DESC' THEN p.price END DESC,
        CASE WHEN :sortBy = 'sku' AND :sortDirection = 'ASC' THEN p.sku END ASC,
        CASE WHEN :sortBy = 'sku' AND :sortDirection = 'DESC' THEN p.sku END DESC,
        CASE WHEN :sortBy = 'offerId' AND :sortDirection = 'ASC' THEN p.offer_id END ASC,
        CASE WHEN :sortBy = 'offerId' AND :sortDirection = 'DESC' THEN p.offer_id END DESC,
        CASE WHEN :sortBy = 'size' AND :sortDirection = 'ASC' THEN p.size END ASC,
        CASE WHEN :sortBy = 'size' AND :sortDirection = 'DESC' THEN p.size END DESC,
        
        CASE WHEN :sortBy = 'stock' AND :sortDirection = 'ASC' 
             THEN (SELECT COALESCE(SUM((s->>'remaining')::integer), 0) 
                   FROM jsonb_array_elements(p.stocks::jsonb->'stocks') AS s) 
        END ASC,
        CASE WHEN :sortBy = 'stock' AND :sortDirection = 'DESC' 
             THEN (SELECT COALESCE(SUM((s->>'remaining')::integer), 0) 
                   FROM jsonb_array_elements(p.stocks::jsonb->'stocks') AS s) 
        END DESC,
        
        CASE WHEN :sortBy = 'barcode' AND :sortDirection = 'ASC' 
             THEN (p.barcodes::jsonb ->> 0) 
        END ASC,
        CASE WHEN :sortBy = 'barcode' AND :sortDirection = 'DESC' 
             THEN (p.barcodes::jsonb ->> 0) 
        END DESC,
        
        CASE WHEN :sortBy = 'tag' AND :sortDirection = 'ASC' 
             THEN (p.tags::jsonb ->> 0) 
        END ASC,
        CASE WHEN :sortBy = 'tag' AND :sortDirection = 'DESC' 
             THEN (p.tags::jsonb ->> 0) 
        END DESC
    """,
            countQuery = """
    SELECT COUNT(*) FROM ozon_products p 
    WHERE p.user_id = :userId AND p.folder_id = :folderId
    AND (
        LOWER(p.name) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR 
        LOWER(p.offer_id) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR 
        CAST(p.sku AS TEXT) LIKE CONCAT('%', :searchTerm, '%') OR 
        LOWER(CAST(p.tags AS TEXT)) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR
        LOWER(CAST(p.barcodes AS TEXT)) LIKE LOWER(CONCAT('%', :searchTerm, '%'))
    )
    """,
            nativeQuery = true)
    Page<OzonProduct> searchProductsInFolderWithSort(
            @Param("userId") Long userId,
            @Param("folderId") Long folderId,
            @Param("searchTerm") String searchTerm,
            @Param("sortBy") String sortBy,
            @Param("sortDirection") String sortDirection,
            Pageable pageable);

    // Поиск товаров без папки
    @Query(value = "SELECT * FROM ozon_products p WHERE p.user_id = :userId AND p.folder_id IS NULL AND " +
            "(LOWER(p.name) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(p.offer_id) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "CAST(p.sku AS TEXT) LIKE CONCAT('%', :searchTerm, '%') OR " +
            "LOWER(CAST(p.tags AS TEXT)) LIKE LOWER(CONCAT('%', :searchTerm, '%')))",
            countQuery = "SELECT COUNT(*) FROM ozon_products p WHERE p.user_id = :userId AND p.folder_id IS NULL AND " +
                    "(LOWER(p.name) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
                    "LOWER(p.offer_id) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
                    "CAST(p.sku AS TEXT) LIKE CONCAT('%', :searchTerm, '%') OR " +
                    "LOWER(CAST(p.tags AS TEXT)) LIKE LOWER(CONCAT('%', :searchTerm, '%')))",
            nativeQuery = true)
    Page<OzonProduct> searchProductsWithoutFolder(@Param("userId") Long userId,
                                                  @Param("searchTerm") String searchTerm,
                                                  Pageable pageable);

    Page<OzonProduct> findByUserIdAndSize(Long userId, String size, Pageable pageable);
}