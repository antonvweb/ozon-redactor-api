package org.ozonLabel.ozonApi.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ozonLabel.common.dto.ozon.CreateProductBySizeDto;
import org.ozonLabel.common.dto.ozon.ProductInfo;
import org.ozonLabel.common.dto.user.UserResponseDto;
import org.ozonLabel.common.service.ozon.FolderService;
import org.ozonLabel.common.service.ozon.OzonService;
import org.ozonLabel.common.service.ozon.ProductCreationService;
import org.ozonLabel.common.service.user.UserService;
import org.ozonLabel.ozonApi.entity.OzonProduct;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductCreationServiceIml implements ProductCreationService {
    private final OzonService ozonService;
    private final UserService userService;
    private final FolderService folderService;

    @Override
    @Transactional
    public ProductInfo createProductBySize(String userEmail, CreateProductBySizeDto dto) {
        UserResponseDto user = getUserByEmail(userEmail);

        if (dto.getSize() == null || dto.getSize().trim().isEmpty()) {
            throw new IllegalArgumentException("Размер не может быть пустым");
        }

        if (dto.getFolderId() != null) {
            validateFolder(user.getId(), dto.getFolderId());
        }

        Long productId = generateLocalProductId();
        while (ozonService.existsByUserIdAndProductId(user.getId(), productId)) {
            productId = generateLocalProductId();
        }

        OzonProduct product = OzonProduct.builder()
                .userId(user.getId())
                .productId(productId)
                .size(dto.getSize().trim())
                .folderId(dto.getFolderId())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        ProductInfo productInfo = mapToProductInfo(product);

// Сохраняем через OzonService
        ProductInfo savedProductInfo = ozonService.saveProduct(productInfo);

        log.info("Создан товар по размеру '{}' (product_id: {}) для пользователя {} в папке {}",
                dto.getSize(), productId, userEmail, dto.getFolderId());

        return savedProductInfo;
    }

    @Override
    @Transactional
    public ProductInfo createProductFromExcel(String userEmail, MultipartFile file, Long folderId) {
        UserResponseDto user = getUserByEmail(userEmail);

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Файл не может быть пустым");
        }

        String filename = file.getOriginalFilename();
        if (filename == null || (!filename.endsWith(".xlsx") && !filename.endsWith(".xls"))) {
            throw new IllegalArgumentException("Поддерживаются только Excel файлы (.xlsx, .xls)");
        }

        if (folderId != null) {
            validateFolder(user.getId(), folderId);
        }

        Long productId = generateLocalProductId();
        while (ozonService.existsByUserIdAndProductId(user.getId(), productId)) {
            productId = generateLocalProductId();
        }

        OzonProduct product = OzonProduct.builder()
                .userId(user.getId())
                .productId(productId)
                .folderId(folderId)
                .name("Импорт из " + filename)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        ProductInfo productInfo = mapToProductInfo(product);

// Сохраняем через OzonService
        ProductInfo savedProductInfo = ozonService.saveProduct(productInfo);

        log.info("Создан товар из Excel файла '{}' (product_id: {}) для пользователя {} в папке {}",
                filename, productId, userEmail, folderId);

        return savedProductInfo;
    }

    private Long generateLocalProductId() {
        return -Math.abs(System.currentTimeMillis() % 1_000_000_000L);
    }

    private void validateFolder(Long userId, Long folderId) {
        if (!folderService.existsByUserIdAndId(userId, folderId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Папка не найдена");
        }
    }

    private UserResponseDto getUserByEmail(String email) {
        return userService.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Пользователь не найден"));
    }

    /**
     * Преобразует сущность OzonProduct в DTO ProductInfo
     */
    private ProductInfo mapToProductInfo(OzonProduct product) {
        ProductInfo info = new ProductInfo();
        product.setUserId(info.getUserId());
        info.setId(product.getProductId());
        info.setName(product.getName());
        info.setSku(product.getSku());
        info.setOfferId(product.getOfferId());
        info.setIsArchived(product.getIsArchived());
        info.setIsAutoarchived(product.getIsAutoarchived());
        info.setPrice(product.getPrice() != null ? product.getPrice().toString() : null);
        info.setOldPrice(product.getOldPrice() != null ? product.getOldPrice().toString() : null);
        info.setMinPrice(product.getMinPrice() != null ? product.getMinPrice().toString() : null);
        info.setCurrencyCode(product.getCurrencyCode());
        info.setFolderId(product.getFolderId());
        info.setSize(product.getSize());
        info.setCreatedAt(product.getCreatedAt() != null ? product.getCreatedAt().toString() : null);
        info.setUpdatedAt(product.getUpdatedAt() != null ? product.getUpdatedAt().toString() : null);
        // JSON-поля оставляем пустыми, при необходимости можно десериализовать
        return info;
    }
}
