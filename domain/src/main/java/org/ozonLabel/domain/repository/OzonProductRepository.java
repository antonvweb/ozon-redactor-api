package org.ozonLabel.domain.repository;

import org.ozonLabel.domain.model.OzonProduct;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OzonProductRepository extends JpaRepository<OzonProduct, Long> {

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

    // Методы для работы с папками
    List<OzonProduct> findByUserIdAndFolderId(Long userId, Long folderId);

    Page<OzonProduct> findByUserIdAndFolderId(Long userId, Long folderId, Pageable pageable);

    List<OzonProduct> findByUserIdAndFolderIdIsNull(Long userId);

    Page<OzonProduct> findByUserIdAndFolderIdIsNull(Long userId, Pageable pageable);

    Long countByUserIdAndFolderId(Long userId, Long folderId);

    Long countByUserIdAndFolderIdIsNull(Long userId);
}