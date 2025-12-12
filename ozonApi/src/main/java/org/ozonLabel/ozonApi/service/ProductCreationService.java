package org.ozonLabel.ozonApi.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ozonLabel.domain.model.OzonProduct;
import org.ozonLabel.domain.model.User;
import org.ozonLabel.domain.repository.OzonProductRepository;
import org.ozonLabel.domain.repository.ProductFolderRepository;
import org.ozonLabel.domain.repository.UserRepository;
import org.ozonLabel.ozonApi.dto.CreateProductBySizeDto;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductCreationService {

    private final OzonProductRepository productRepository;
    private final UserRepository userRepository;
    private final ProductFolderRepository folderRepository;

    /**
     * Создать товар по размеру
     */
    @Transactional
    public OzonProduct createProductBySize(String userEmail, CreateProductBySizeDto dto) {
        User user = getUserByEmail(userEmail);

        // Валидация размера
        if (dto.getSize() == null || dto.getSize().trim().isEmpty()) {
            throw new IllegalArgumentException("Размер не может быть пустым");
        }

        // Проверяем существование папки, если указана
        if (dto.getFolderId() != null) {
            validateFolder(user.getId(), dto.getFolderId());
        }

        // Генерируем уникальный product_id для локального товара
        Long productId = generateLocalProductId();

        // Проверяем уникальность
        while (productRepository.existsByUserIdAndProductId(user.getId(), productId)) {
            productId = generateLocalProductId();
        }

        // Создаем продукт с обязательным product_id
        OzonProduct product = OzonProduct.builder()
                .userId(user.getId())
                .productId(productId) // ОБЯЗАТЕЛЬНОЕ ПОЛЕ!
                .size(dto.getSize().trim())
                .folderId(dto.getFolderId())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        product = productRepository.save(product);

        log.info("Создан товар по размеру '{}' (product_id: {}) для пользователя {} в папке {}",
                dto.getSize(), productId, userEmail, dto.getFolderId());

        return product;
    }

    private Long generateLocalProductId() {
        // Генерируем уникальный отрицательный ID для локальных товаров
        // Отрицательные значения помогут отличать от товаров Ozon API
        return -Math.abs(System.currentTimeMillis() % 1000000000L);
    }

    /**
     * Создать товар из Excel файла
     */
    @Transactional
    public OzonProduct createProductFromExcel(String userEmail, MultipartFile file, Long folderId) {
        User user = getUserByEmail(userEmail);

        // Валидация файла
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Файл не может быть пустым");
        }

        // Проверяем тип файла
        String contentType = file.getContentType();
        String filename = file.getOriginalFilename();

        if (filename == null || (!filename.endsWith(".xlsx") && !filename.endsWith(".xls"))) {
            throw new IllegalArgumentException("Поддерживаются только Excel файлы (.xlsx, .xls)");
        }

        // Проверяем существование папки, если указана
        if (folderId != null) {
            validateFolder(user.getId(), folderId);
        }

        // Генерируем уникальный product_id
        Long productId = generateLocalProductId();
        while (productRepository.existsByUserIdAndProductId(user.getId(), productId)) {
            productId = generateLocalProductId();
        }

        // Создаем запись с обязательным product_id
        OzonProduct product = OzonProduct.builder()
                .userId(user.getId())
                .productId(productId) // ОБЯЗАТЕЛЬНОЕ ПОЛЕ!
                .folderId(folderId)
                .name("Импорт из " + filename)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        product = productRepository.save(product);

        log.info("Создан товар из Excel файла '{}' (product_id: {}) для пользователя {} в папке {}",
                filename, productId, userEmail, folderId);

        try {
            log.debug("Размер загруженного файла: {} байт", file.getSize());
        } catch (Exception e) {
            log.warn("Не удалось получить размер файла", e);
        }

        return product;
    }

    /**
     * Валидация существования папки
     */
    private void validateFolder(Long userId, Long folderId) {
        if (!folderRepository.existsByUserIdAndId(userId, folderId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Папка не найдена");
        }
    }

    /**
     * Получить пользователя по email
     */
    private User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Пользователь не найден"));
    }
}