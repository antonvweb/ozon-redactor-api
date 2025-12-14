package org.ozonLabel.common.service.ozon;

import org.ozonLabel.common.dto.ozon.*;
import org.ozonLabel.common.exception.ozon.OzonApiCredentialsMissingException;
import org.ozonLabel.common.exception.ozon.OzonApiException;
import org.ozonLabel.common.exception.ozon.UserNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface OzonService {

    // ==== Синхронизация товаров ====
    SyncProductsResponse syncProducts(Long userId, SyncProductsRequest request, Long folderId)
            throws UserNotFoundException, OzonApiCredentialsMissingException, OzonApiException;

    SyncProductsResponse syncProducts(Long userId, SyncProductsRequest request)
            throws UserNotFoundException, OzonApiCredentialsMissingException, OzonApiException;

    // ==== Преобразование для фронтенда ====
    ProductFrontendResponse toFrontendResponse(ProductInfo product);
    ProductFrontendResponse mapToFrontendResponse(ProductInfo productInfo);

    // ==== Основные CRUD методы ====
    boolean existsByUserIdAndProductId(Long userId, Long productId);
    Optional<ProductInfo> findByUserIdAndProductId(Long userId, Long productId);
    List<ProductInfo> findByUserId(Long userId);
    List<ProductInfo> findByUserIdOrderByUpdatedAtDesc(Long userId);
    Page<ProductInfo> findByUserIdOrderByUpdatedAtDesc(Long userId, Pageable pageable);
    void deleteByUserId(Long userId);
    Long countByUserId(Long userId);

    // ==== Методы по назначению товаров ====
    List<ProductInfo> findByUserIdAndAssignedToUserId(Long userId, Long assignedToUserId);
    Page<ProductInfo> findByUserIdAndAssignedToUserId(Long userId, Long assignedToUserId, Pageable pageable);
    List<ProductInfo> findByUserIdAndAssignedToUserIdIsNull(Long userId);
    Page<ProductInfo> findByUserIdAndAssignedToUserIdIsNull(Long userId, Pageable pageable);
    Page<ProductInfo> findByUserIdAndNameContainingIgnoreCase(Long userId, String name, Pageable pageable);
    Page<ProductInfo> findByUserIdAndAssignedToUserIdAndNameContainingIgnoreCase(Long userId, Long assignedToUserId, String name, Pageable pageable);
    Long countByUserIdAndAssignedToUserId(Long userId, Long assignedToUserId);
    Long countByUserIdAndAssignedToUserIdIsNull(Long userId);

    int bulkAssignProducts(List<Long> productIds, Long companyOwnerId, Long assignedUserId);
    int bulkMoveProductsToFolder(List<Long> productIds, Long userId, Long folderId);

    // ==== Методы по папкам ====
    List<ProductInfo> findByUserIdAndFolderId(Long userId, Long folderId);
    Page<ProductInfo> findByUserIdAndFolderId(Long userId, Long folderId, Pageable pageable);
    List<ProductInfo> findByUserIdAndFolderIdIsNull(Long userId);
    Page<ProductInfo> findByUserIdAndFolderIdIsNull(Long userId, Pageable pageable);
    Long countByUserIdAndFolderId(Long userId, Long folderId);
    Long countByUserIdAndFolderIdIsNull(Long userId);

    // ==== Методы по размеру ====
    List<ProductInfo> findByUserIdAndSize(Long userId, String size);
    Page<ProductInfo> findByUserIdAndSize(Long userId, String size, Pageable pageable);

    ProductInfo saveProduct(ProductInfo productInfo);
}

