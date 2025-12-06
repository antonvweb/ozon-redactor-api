package org.ozonLabel.ozonApi.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ozonLabel.domain.repository.OzonProductRepository;
import org.ozonLabel.ozonApi.dto.SyncProductsRequest;
import org.ozonLabel.ozonApi.dto.SyncProductsResponse;
import org.ozonLabel.domain.model.OzonProduct;
import org.ozonLabel.ozonApi.service.OzonService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ozon")
@RequiredArgsConstructor
@Slf4j
public class OzonController {

    private final OzonService ozonService;
    private final OzonProductRepository productRepository;

    /**
     * Синхронизирует товары из Ozon API
     */
    @PostMapping("/sync/{userId}")
    public ResponseEntity<SyncProductsResponse> syncProducts(
            @PathVariable Long userId,
            @RequestBody(required = false) SyncProductsRequest request) {

        log.info("Начало синхронизации товаров для пользовател: {}", userId);

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

        SyncProductsResponse response = ozonService.syncProducts(userId, request);

        log.info("Синхронизация завершена. Обработано товаров: {}", response.getProducts().size());

        return ResponseEntity.ok(response);
    }

    /**
     * Альтернативная ручка с параметрами в query string
     */
    @GetMapping("/sync/{userId}")
    public ResponseEntity<SyncProductsResponse> syncProductsSimple(
            @PathVariable Long userId,
            @RequestParam(required = false, defaultValue = "") String lastId,
            @RequestParam(required = false, defaultValue = "100") Integer limit) {

        SyncProductsRequest request = SyncProductsRequest.builder()
                .filter(new HashMap<>())
                .lastId(lastId)
                .limit(limit)
                .build();

        return syncProducts(userId, request);
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
}