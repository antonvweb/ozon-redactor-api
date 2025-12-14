package org.ozonLabel.ozonApi.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ozonLabel.common.dto.ozon.*;
import org.ozonLabel.common.service.ozon.OzonService;
import org.ozonLabel.common.service.ozon.ProductCreationService;
import org.ozonLabel.common.service.user.CompanyService;
import org.ozonLabel.ozonApi.entity.OzonProduct;
import org.ozonLabel.ozonApi.repository.OzonProductRepository;
import org.ozonLabel.ozonApi.service.OzonServiceIml;
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
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/ozon")
@RequiredArgsConstructor
@Slf4j
public class OzonController {

    private final OzonService ozonService;
    private final ProductCreationService productCreationService;
    private final OzonProductRepository productRepository;
    private final CompanyService companyService;
    private final ObjectMapper objectMapper;

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
    public ResponseEntity<ProductFrontendResponse> createProductBySize(
            @RequestBody CreateProductBySizeDto dto,
            Authentication auth) {
        String userEmail = auth.getName();
        log.info("Создание товара по размеру '{}' для пользователя {} в папке {}",
                dto.getSize(), userEmail, dto.getFolderId());
        ProductInfo product = productCreationService.createProductBySize(userEmail, dto);
        return ResponseEntity.ok(ozonService.toFrontendResponse(product));
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

        ProductInfo product = productCreationService.createProductFromExcel(userEmail, file, folderId);
        ProductFrontendResponse frontend = ozonService.toFrontendResponse(product);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Файл успешно загружен");
        response.put("productId", product.getId());
        response.put("filename", file.getOriginalFilename());
        response.put("product", frontend); // добавляем готовый объект для фронта

        return ResponseEntity.ok(response);
    }

    ProductInfo mapToProductInfo(OzonProduct product) {
        if (product == null) return null;
        try {
            return new ProductInfo(
                    product.getProductId(),
                    product.getName(),
                    product.getFolderId(),
                    product.getSize(),
                    product.getOfferId(),
                    product.getIsArchived(),
                    product.getIsAutoarchived(),
                    parseJson(product.getBarcodes(), new TypeReference<List<String>>() {}),
                    product.getDescriptionCategoryId(),
                    product.getTypeId(),
                    product.getProductCreatedAt() != null ? product.getProductCreatedAt().toString() : null,
                    parseJson(product.getImages(), new TypeReference<List<String>>() {}),
                    product.getCurrencyCode(),
                    product.getMinPrice() != null ? product.getMinPrice().toString() : null,
                    product.getOldPrice() != null ? product.getOldPrice().toString() : null,
                    product.getPrice() != null ? product.getPrice().toString() : null,
                    parseJson(product.getSources(), new TypeReference<List<Map<String, Object>>>() {}),
                    parseJson(product.getModel_info(), new TypeReference<Map<String, Object>>() {}),
                    parseJson(product.getCommissions(), new TypeReference<List<Map<String, Object>>>() {}),
                    product.getIsPrepaymentAllowed(),
                    product.getVolumeWeight() != null ? product.getVolumeWeight().doubleValue() : null,
                    product.getHasDiscountedFboItem(),
                    product.getIsDiscounted(),
                    product.getDiscountedFboStocks(),
                    parseJson(product.getStocks(), new TypeReference<Map<String, Object>>() {}),
                    parseJson(product.getErrors(), new TypeReference<List<Map<String, Object>>>() {}),
                    product.getProductUpdatedAt() != null ? product.getProductUpdatedAt().toString() : null,
                    product.getVat() != null ? product.getVat().toString() : null,
                    parseJson(product.getVisibility_details(), new TypeReference<Map<String, Object>>() {}),
                    parseJson(product.getPrice_indexes(), new TypeReference<Map<String, Object>>() {}),
                    parseJson(product.getImages360(), new TypeReference<List<String>>() {}),
                    product.getIsKgt(),
                    parseJson(product.getColor_image(), new TypeReference<List<String>>() {}),
                    parseJson(product.getPrimary_image(), new TypeReference<List<String>>() {}),
                    parseJson(product.getStatuses(), new TypeReference<Map<String, Object>>() {}),
                    product.getIsSuper(),
                    product.getIsSeasonal(),
                    parseJson(product.getPromotions(), new TypeReference<List<Map<String, Object>>>() {}),
                    product.getSku(),
                    parseJson(product.getAvailabilities(), new TypeReference<List<Map<String, Object>>>() {})
            );
        } catch (Exception e) {
            throw new RuntimeException("Ошибка маппинга OzonProduct в ProductInfo", e);
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T parseJson(String json, TypeReference<T> typeReference) {
        if (json == null || json.isEmpty()) return null;
        try {
            return objectMapper.readValue(json, typeReference);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Получить товары из папки
     */
    @GetMapping("/products/folder/{folderId}")
    public ResponseEntity<Map<String, Object>> getProductsInFolder(
            @PathVariable Long folderId,
            @RequestParam Long companyOwnerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication auth) {

        String userEmail = auth.getName();
        // Проверяем доступ к компании
        companyService.checkAccess(userEmail, companyOwnerId);
        log.info("Получение товаров из папки {} для пользователя {}", folderId, companyOwnerId);
        Pageable pageable = PageRequest.of(page, size);
        Page<OzonProduct> productsPage = productRepository.findByUserIdAndFolderId(companyOwnerId, folderId, pageable);
        List<ProductFrontendResponse> responses = productsPage.getContent().stream()
                .map(product -> ozonService.toFrontendResponse(mapToProductInfo(product)))
                .collect(Collectors.toList());
        Map<String, Object> response = new HashMap<>();
        response.put("products", responses);
        response.put("currentPage", productsPage.getNumber());
        response.put("totalPages", productsPage.getTotalPages());
        response.put("totalElements", productsPage.getTotalElements());
        return ResponseEntity.ok(response);
    }

    /**
     * Получить товары без папки
     */
    @GetMapping("/products/no-folder")
    public ResponseEntity<Map<String, Object>> getProductsWithoutFolder(
            @RequestParam Long companyOwnerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication auth) {

        String userEmail = auth.getName();
        companyService.checkAccess(userEmail, companyOwnerId);
        log.info("Получение товаров без папки для пользователя {}", companyOwnerId);
        Pageable pageable = PageRequest.of(page, size);
        Page<OzonProduct> productsPage = productRepository.findByUserIdAndFolderIdIsNull(companyOwnerId, pageable);
        List<ProductFrontendResponse> responses = productsPage.getContent().stream()
                .map(product -> ozonService.toFrontendResponse(mapToProductInfo(product)))
                .collect(Collectors.toList());
        Map<String, Object> response = new HashMap<>();
        response.put("products", responses);
        response.put("currentPage", productsPage.getNumber());
        response.put("totalPages", productsPage.getTotalPages());
        response.put("totalElements", productsPage.getTotalElements());
        return ResponseEntity.ok(response);
    }

    /**
     * Получить все товары пользователя
     */
    @GetMapping("/products")
    public ResponseEntity<List<ProductFrontendResponse>> getAllProducts(
            @RequestParam Long companyOwnerId,
            Authentication auth) {

        String userEmail = auth.getName();
        companyService.checkAccess(userEmail, companyOwnerId);
        log.info("Получение всех товаров для пользователя {}", companyOwnerId);
        List<OzonProduct> products = productRepository.findByUserId(companyOwnerId);
        List<ProductFrontendResponse> responses = products.stream()
                .map(product -> ozonService.toFrontendResponse(mapToProductInfo(product)))
                .collect(Collectors.toList());
        return ResponseEntity.ok(responses);
    }

    /**
     * Получить товары по размеру
     */
    @GetMapping("/products/by-size")
    public ResponseEntity<List<ProductFrontendResponse>> getProductsBySize(
            @RequestParam Long companyOwnerId,
            @RequestParam String size,
            Authentication auth) {

        String userEmail = auth.getName();
        companyService.checkAccess(userEmail, companyOwnerId);
        log.info("Поиск товаров по размеру '{}' для пользователя {}", size, companyOwnerId);
        List<OzonProduct> products = productRepository.findByUserIdAndSize(companyOwnerId, size);
        List<ProductFrontendResponse> responses = products.stream()
                .map(product -> ozonService.toFrontendResponse(mapToProductInfo(product)))
                .collect(Collectors.toList());
        return ResponseEntity.ok(responses);
    }
}