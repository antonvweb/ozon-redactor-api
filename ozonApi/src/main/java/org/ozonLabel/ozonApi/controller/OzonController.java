package org.ozonLabel.ozonApi.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ozonLabel.common.dto.ApiResponse;
import org.ozonLabel.ozonApi.dto.CreateProductBySizeDto;
import org.ozonLabel.ozonApi.dto.SyncProductsRequest;
import org.ozonLabel.ozonApi.dto.SyncProductsResponse;
import org.ozonLabel.domain.model.OzonProduct;
import org.ozonLabel.domain.repository.OzonProductRepository;
import org.ozonLabel.ozonApi.service.OzonService;
import org.ozonLabel.ozonApi.service.ProductCreationService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/ozon")
@RequiredArgsConstructor
@Slf4j
public class OzonController {

    private final OzonService ozonService;
    private final ProductCreationService productCreationService;
    private final OzonProductRepository productRepository;

    /**
     * Синхронизирует товары из Ozon API с возможностью указания папки
     */
    @PostMapping("/sync/{userId}")
    public ResponseEntity<SyncProductsResponse> syncProducts(
            @PathVariable Long userId,
            @RequestParam(required = false) Long folderId,
            @RequestBody(required = false) SyncProductsRequest request) {

        log.info("Начало синхронизации товаров для пользователя: {} в папку: {}", userId, folderId);

        if (request == null) {
            request = SyncProductsRequest.builder()
                    .filter(new HashMap<>())
                    .lastId("")
                    .limit(100)
                    .build();
        }

        if (request.getFilter() == null) {
            request.setFilter(new HashMap<>());
        }
        if (request.getLastId() == null) {
            request.setLastId("");
        }
        if (request.getLimit() == null) {
            request.setLimit(100);
        }

        SyncProductsResponse response = ozonService.syncProducts(userId, request, folderId);

        log.info("Синхронизация завершена. Обработано товаров: {}", response.getProducts().size());

        return ResponseEntity.ok(response);
    }

    /**
     * Альтернативная ручка с параметрами в query string
     */
    @GetMapping("/sync/{userId}")
    public ResponseEntity<SyncProductsResponse> syncProductsSimple(
            @PathVariable Long userId,
            @RequestParam(required = false) Long folderId,
            @RequestParam(required = false, defaultValue = "") String lastId,
            @RequestParam(required = false, defaultValue = "100") Integer limit) {

        SyncProductsRequest request = SyncProductsRequest.builder()
                .filter(new HashMap<>())
                .lastId(lastId)
                .limit(limit)
                .build();

        return syncProducts(userId, folderId, request);
    }

    /**
     * Создать товар по размеру
     */
    @PostMapping("/products/by-size")
    public ResponseEntity<OzonProduct> createProductBySize(
            @RequestBody CreateProductBySizeDto dto,
            Authentication auth) {

        String userEmail = auth.getName();
        log.info("Создание товара по размеру '{}' для пользователя {} в папке {}",
                dto.getSize(), userEmail, dto.getFolderId());

        OzonProduct product = productCreationService.createProductBySize(userEmail, dto);
        return ResponseEntity.ok(product);
    }

    /**
     * Создать товар из Excel файла
     */
    @PostMapping("/products/upload-excel")
    public ResponseEntity<Map<String, Object>> uploadExcel(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) Long folderId,
            Authentication auth) {

        String userEmail = auth.getName();
        log.info("Загрузка Excel файла '{}' для пользователя {} в папку {}",
                file.getOriginalFilename(), userEmail, folderId);

        OzonProduct product = productCreationService.createProductFromExcel(userEmail, file, folderId);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Файл успешно загружен");
        response.put("productId", product.getId());
        response.put("filename", file.getOriginalFilename());

        return ResponseEntity.ok(response);
    }

    /**
     * Получить товары из папки
     */
    @GetMapping("/products/folder/{folderId}")
    public ResponseEntity<Map<String, Object>> getProductsInFolder(
            @PathVariable Long folderId,
            @RequestParam Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        log.info("Получение товаров из папки {} для пользователя {}", folderId, userId);

        Pageable pageable = PageRequest.of(page, size);
        Page<OzonProduct> products = productRepository.findByUserIdAndFolderId(userId, folderId, pageable);

        Map<String, Object> response = new HashMap<>();
        response.put("products", products.getContent());
        response.put("currentPage", products.getNumber());
        response.put("totalPages", products.getTotalPages());
        response.put("totalElements", products.getTotalElements());

        return ResponseEntity.ok(response);
    }

    /**
     * Получить товары без папки
     */
    @GetMapping("/products/no-folder")
    public ResponseEntity<Map<String, Object>> getProductsWithoutFolder(
            @RequestParam Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        log.info("Получение товаров без папки для пользователя {}", userId);

        Pageable pageable = PageRequest.of(page, size);
        Page<OzonProduct> products = productRepository.findByUserIdAndFolderIdIsNull(userId, pageable);

        Map<String, Object> response = new HashMap<>();
        response.put("products", products.getContent());
        response.put("currentPage", products.getNumber());
        response.put("totalPages", products.getTotalPages());
        response.put("totalElements", products.getTotalElements());

        return ResponseEntity.ok(response);
    }

    /**
     * Получить все товары пользователя
     */
    @GetMapping("/products")
    public ResponseEntity<List<OzonProduct>> getAllProducts(@RequestParam Long userId) {
        log.info("Получение всех товаров для пользователя {}", userId);

        List<OzonProduct> products = productRepository.findByUserId(userId);
        return ResponseEntity.ok(products);
    }

    /**
     * Получить товары по размеру
     */
    @GetMapping("/products/by-size")
    public ResponseEntity<List<OzonProduct>> getProductsBySize(
            @RequestParam Long userId,
            @RequestParam String size) {

        log.info("Поиск товаров по размеру '{}' для пользователя {}", size, userId);

        List<OzonProduct> products = productRepository.findByUserIdAndSize(userId, size);
        return ResponseEntity.ok(products);
    }
}