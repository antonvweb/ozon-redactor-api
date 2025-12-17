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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.*;
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

    /**
     * Добавить тег к товару
     */
    @PostMapping("/products/{productId}/tags")
    public ResponseEntity<ProductFrontendResponse> addTagToProduct(
            @PathVariable Long productId,
            @RequestParam Long companyOwnerId,
            @RequestBody AddTagRequest request,
            Authentication auth) {

        String userEmail = auth.getName();
        companyService.checkAccess(userEmail, companyOwnerId);

        log.info("Добавление тега '{}' к товару {} для пользователя {}",
                request.getTag(), productId, companyOwnerId);

        // Находим товар
        OzonProduct product = productRepository.findByUserIdAndProductId(companyOwnerId, productId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Товар не найден"));

        // Получаем текущие теги
        List<String> currentTags = parseJson(product.getTags(), new TypeReference<List<String>>() {});
        if (currentTags == null) {
            currentTags = new ArrayList<>();
        }

        // Добавляем новый тег, если его еще нет
        if (!currentTags.contains(request.getTag())) {
            currentTags.add(request.getTag());
            product.setTags(serializeToJson(currentTags));
            productRepository.save(product);
        }

        return ResponseEntity.ok(ozonService.toFrontendResponse(mapToProductInfo(product)));
    }

    private String serializeToJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.error("Ошибка сериализации в JSON", e);
            return null;
        }
    }

    /**
     * Удалить тег у товара
     */
    @DeleteMapping("/products/{productId}/tags")
    public ResponseEntity<ProductFrontendResponse> removeTagFromProduct(
            @PathVariable Long productId,
            @RequestParam Long companyOwnerId,
            @RequestBody RemoveTagRequest request,
            Authentication auth) {

        String userEmail = auth.getName();
        companyService.checkAccess(userEmail, companyOwnerId);

        log.info("Удаление тега '{}' у товара {} для пользователя {}",
                request.getTag(), productId, companyOwnerId);

        // Находим товар
        OzonProduct product = productRepository.findByUserIdAndProductId(companyOwnerId, productId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Товар не найден"));

        // Получаем текущие теги
        List<String> currentTags = parseJson(product.getTags(), new TypeReference<List<String>>() {});
        if (currentTags != null && currentTags.contains(request.getTag())) {
            currentTags.remove(request.getTag());
            product.setTags(serializeToJson(currentTags));
            productRepository.save(product);
        }

        return ResponseEntity.ok(ozonService.toFrontendResponse(mapToProductInfo(product)));
    }

    /**
     * Обновить все теги товара
     */
    @PutMapping("/products/{productId}/tags")
    public ResponseEntity<ProductFrontendResponse> updateProductTags(
            @PathVariable Long productId,
            @RequestParam Long companyOwnerId,
            @RequestBody UpdateTagsRequest request,
            Authentication auth) {

        String userEmail = auth.getName();
        companyService.checkAccess(userEmail, companyOwnerId);

        log.info("Обновление тегов товара {} для пользователя {}. Новые теги: {}",
                productId, companyOwnerId, request.getTags());

        // Находим товар
        OzonProduct product = productRepository.findByUserIdAndProductId(companyOwnerId, productId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Товар не найден"));

        // Обновляем теги
        product.setTags(serializeToJson(request.getTags()));
        productRepository.save(product);

        return ResponseEntity.ok(ozonService.toFrontendResponse(mapToProductInfo(product)));
    }

    @PostMapping("/products/bulk/tags")
    public ResponseEntity<Map<String, Object>> bulkAddTagsToProducts(
            @RequestParam Long companyOwnerId,
            @RequestBody BulkUpdateTagsRequest request,
            Authentication auth) {

        String userEmail = auth.getName();
        companyService.checkAccess(userEmail, companyOwnerId);

        // Проверяем, что tags не null и не пустой
        if (request.getTag() == null || request.getTag().trim().isEmpty()) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Тег не может быть пустым");
            return ResponseEntity.badRequest().body(errorResponse);
        }

        String tag = request.getTag().trim();

        log.info("Массовое добавление тега '{}' к {} товарам для пользователя {}",
                tag, request.getProductIds().size(), companyOwnerId);

        List<OzonProduct> updatedProducts = new ArrayList<>();
        int updatedCount = 0;

        for (Long productId : request.getProductIds()) {
            OzonProduct product = productRepository.findByUserIdAndProductId(companyOwnerId, productId)
                    .orElse(null);

            if (product != null) {
                List<String> currentTags = parseJson(product.getTags(), new TypeReference<List<String>>() {});
                if (currentTags == null) {
                    currentTags = new ArrayList<>();
                }

                // Добавляем тег, если его еще нет
                if (!currentTags.contains(tag)) {
                    currentTags.add(tag);
                    product.setTags(serializeToJson(currentTags));
                    productRepository.save(product);
                    updatedProducts.add(product);
                    updatedCount++;
                }
            }
        }

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", String.format("Тег '%s' добавлен к %d товарам", tag, updatedCount));
        response.put("updatedCount", updatedCount);
        response.put("totalRequested", request.getProductIds().size());

        return ResponseEntity.ok(response);
    }

    ProductInfo mapToProductInfo(OzonProduct product) {
        if (product == null) return null;
        try {
            return ProductInfo.builder()
                    .userId(product.getUserId())
                    .id(product.getProductId())
                    .name(product.getName())
                    .folderId(product.getFolderId())
                    .size(product.getSize())
                    .tags(parseJson(product.getTags(), new TypeReference<List<String>>() {})) // Вот здесь
                    .offerId(product.getOfferId())
                    .isArchived(product.getIsArchived())
                    .isAutoarchived(product.getIsAutoarchived())
                    .barcodes(parseJson(product.getBarcodes(), new TypeReference<List<String>>() {}))
                    .descriptionCategoryId(product.getDescriptionCategoryId())
                    .typeId(product.getTypeId())
                    .createdAt(product.getProductCreatedAt() != null ? product.getProductCreatedAt().toString() : null)
                    .images(parseJson(product.getImages(), new TypeReference<List<String>>() {}))
                    .currencyCode(product.getCurrencyCode())
                    .minPrice(product.getMinPrice() != null ? product.getMinPrice().toString() : null)
                    .oldPrice(product.getOldPrice() != null ? product.getOldPrice().toString() : null)
                    .price(product.getPrice() != null ? product.getPrice().toString() : null)
                    .sources(parseJson(product.getSources(), new TypeReference<List<Map<String, Object>>>() {}))
                    .modelInfo(parseJson(product.getModel_info(), new TypeReference<Map<String, Object>>() {}))
                    .commissions(parseJson(product.getCommissions(), new TypeReference<List<Map<String, Object>>>() {}))
                    .isPrepaymentAllowed(product.getIsPrepaymentAllowed())
                    .volumeWeight(product.getVolumeWeight() != null ? product.getVolumeWeight().doubleValue() : null)
                    .hasDiscountedFboItem(product.getHasDiscountedFboItem())
                    .isDiscounted(product.getIsDiscounted())
                    .discountedFboStocks(product.getDiscountedFboStocks())
                    .stocks(parseJson(product.getStocks(), new TypeReference<Map<String, Object>>() {}))
                    .errors(parseJson(product.getErrors(), new TypeReference<List<Map<String, Object>>>() {}))
                    .updatedAt(product.getProductUpdatedAt() != null ? product.getProductUpdatedAt().toString() : null)
                    .vat(product.getVat() != null ? product.getVat().toString() : null)
                    .visibilityDetails(parseJson(product.getVisibility_details(), new TypeReference<Map<String, Object>>() {}))
                    .priceIndexes(parseJson(product.getPrice_indexes(), new TypeReference<Map<String, Object>>() {}))
                    .images360(parseJson(product.getImages360(), new TypeReference<List<String>>() {}))
                    .isKgt(product.getIsKgt())
                    .colorImage(parseJson(product.getColor_image(), new TypeReference<List<String>>() {}))
                    .primaryImage(parseJson(product.getPrimary_image(), new TypeReference<List<String>>() {}))
                    .statuses(parseJson(product.getStatuses(), new TypeReference<Map<String, Object>>() {}))
                    .isSuper(product.getIsSuper())
                    .isSeasonal(product.getIsSeasonal())
                    .promotions(parseJson(product.getPromotions(), new TypeReference<List<Map<String, Object>>>() {}))
                    .sku(product.getSku())
                    .availabilities(parseJson(product.getAvailabilities(), new TypeReference<List<Map<String, Object>>>() {}))
                    .build();
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
    /**
     * Получить товары из папки
     */
    @GetMapping("/products/folder/{folderId}")
    public ResponseEntity<Map<String, Object>> getProductsInFolder(
            @PathVariable Long folderId,
            @RequestParam Long companyOwnerId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false, defaultValue = "DESC") String sortDirection,
            @RequestParam(required = false) String statusFilter,  // ← НОВЫЙ ПАРАМЕТР
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication auth) {

        String userEmail = auth.getName();
        companyService.checkAccess(userEmail, companyOwnerId);

        Pageable pageable = PageRequest.of(page, size);
        String searchTerm = (search != null) ? search.trim() : "";

        Page<OzonProduct> productsPage = productRepository.searchProductsInFolder(companyOwnerId, folderId, searchTerm, pageable);
        log.info("Получение товаров из папки {} для пользователя {} с поиском '{}'", folderId, companyOwnerId, searchTerm);

        List<OzonProduct> content = new ArrayList<>(productsPage.getContent());

        // === НОВАЯ ФИЛЬТРАЦИЯ ПО СТАТУСУ ===
        if (statusFilter != null && !statusFilter.trim().isEmpty()) {
            String filter = statusFilter.trim();
            content = content.stream()
                    .filter(product -> {
                        Map<String, Object> statusesMap = parseJson(product.getStatuses(), new TypeReference<Map<String, Object>>() {});
                        if (statusesMap == null || !statusesMap.containsKey("status_name")) {
                            return false;
                        }
                        String statusName = (String) statusesMap.get("status_name");
                        return statusName != null && statusName.equals(filter);
                    })
                    .collect(Collectors.toList());
        }

        String effectiveSortBy = (sortBy != null && !sortBy.trim().isEmpty()) ? sortBy.trim() : "updatedAt";
        String effectiveSortDirection = (sortBy != null && !sortBy.trim().isEmpty()) ? sortDirection : "DESC";
        sortProducts(content, effectiveSortBy, effectiveSortDirection);

        long totalElements = (statusFilter != null && !statusFilter.trim().isEmpty())
                ? content.size()
                : productsPage.getTotalElements();

        productsPage = new PageImpl<>(content, pageable, totalElements);

        List<ProductFrontendResponse> responses = content.stream()
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
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false, defaultValue = "DESC") String sortDirection,
            @RequestParam(required = false) String statusFilter,  // ← НОВЫЙ ПАРАМЕТР
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication auth) {

        String userEmail = auth.getName();
        companyService.checkAccess(userEmail, companyOwnerId);

        Pageable pageable = PageRequest.of(page, size);
        String searchTerm = (search != null) ? search.trim() : "";

        Page<OzonProduct> productsPage = productRepository.searchProductsWithoutFolder(companyOwnerId, searchTerm, pageable);
        log.info("Получение товаров без папки для пользователя {} с поиском '{}'", companyOwnerId, searchTerm);

        List<OzonProduct> content = new ArrayList<>(productsPage.getContent());

        // === НОВАЯ ФИЛЬТРАЦИЯ ПО СТАТУСУ ===
        if (statusFilter != null && !statusFilter.trim().isEmpty()) {
            String filter = statusFilter.trim();
            content = content.stream()
                    .filter(product -> {
                        Map<String, Object> statusesMap = parseJson(product.getStatuses(), new TypeReference<Map<String, Object>>() {});
                        if (statusesMap == null || !statusesMap.containsKey("status_name")) {
                            return false;
                        }
                        String statusName = (String) statusesMap.get("status_name");
                        return statusName != null && statusName.equals(filter);
                    })
                    .collect(Collectors.toList());
        }

        String effectiveSortBy = (sortBy != null && !sortBy.trim().isEmpty()) ? sortBy.trim() : "updatedAt";
        String effectiveSortDirection = (sortBy != null && !sortBy.trim().isEmpty()) ? sortDirection : "DESC";
        sortProducts(content, effectiveSortBy, effectiveSortDirection);

        long totalElements = (statusFilter != null && !statusFilter.trim().isEmpty())
                ? content.size()
                : productsPage.getTotalElements();

        productsPage = new PageImpl<>(content, pageable, totalElements);

        List<ProductFrontendResponse> responses = content.stream()
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
    public ResponseEntity<Map<String, Object>> getAllProducts(
            @RequestParam Long companyOwnerId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false, defaultValue = "DESC") String sortDirection,
            @RequestParam(required = false) String statusFilter,  // ← НОВЫЙ ПАРАМЕТР
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication auth) {

        String userEmail = auth.getName();
        companyService.checkAccess(userEmail, companyOwnerId);

        Pageable pageable = PageRequest.of(page, size);
        String searchTerm = (search != null) ? search.trim() : "";

        Page<OzonProduct> productsPage = productRepository.searchProducts(companyOwnerId, searchTerm, pageable);
        log.info("Получение всех товаров для пользователя {} с поиском '{}'", companyOwnerId, searchTerm);

        List<OzonProduct> content = new ArrayList<>(productsPage.getContent());

        // === НОВАЯ ФИЛЬТРАЦИЯ ПО СТАТУСУ ===
        if (statusFilter != null && !statusFilter.trim().isEmpty()) {
            String filter = statusFilter.trim();
            content = content.stream()
                    .filter(product -> {
                        Map<String, Object> statusesMap = parseJson(product.getStatuses(), new TypeReference<Map<String, Object>>() {});
                        if (statusesMap == null || !statusesMap.containsKey("status_name")) {
                            return false;
                        }
                        String statusName = (String) statusesMap.get("status_name");
                        return statusName != null && statusName.equals(filter);
                    })
                    .collect(Collectors.toList());
        }

        String effectiveSortBy = (sortBy != null && !sortBy.trim().isEmpty()) ? sortBy.trim() : "updatedAt";
        String effectiveSortDirection = (sortBy != null && !sortBy.trim().isEmpty()) ? sortDirection : "DESC";
        sortProducts(content, effectiveSortBy, effectiveSortDirection);

        // Общее количество после фильтрации (для клиентской фильтрации — только видимые на странице + оригинальный total)
        long totalElements = (statusFilter != null && !statusFilter.trim().isEmpty())
                ? content.size()  // неточний total, но приемлемо для небольших данных
                : productsPage.getTotalElements();

        productsPage = new PageImpl<>(content, pageable, totalElements);

        List<ProductFrontendResponse> responses = content.stream()
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
     * Получить товары по размеру
     */
    @GetMapping("/products/by-size")
    public ResponseEntity<Map<String, Object>> getProductsBySize(
            @RequestParam Long companyOwnerId,
            @RequestParam String size,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false, defaultValue = "DESC") String sortDirection,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            Authentication auth) {

        String userEmail = auth.getName();
        companyService.checkAccess(userEmail, companyOwnerId);
        log.info("Поиск товаров по размеру '{}' для пользователя {}", size, companyOwnerId);

        Pageable pageable = PageRequest.of(page, pageSize);
        Page<OzonProduct> productsPage = productRepository.findByUserIdAndSize(companyOwnerId, size, pageable);

        List<OzonProduct> content = new ArrayList<>(productsPage.getContent());
        String effectiveSortBy = (sortBy != null && !sortBy.trim().isEmpty()) ? sortBy.trim() : "updatedAt";
        String effectiveSortDirection = (sortBy != null && !sortBy.trim().isEmpty()) ? sortDirection : "DESC";
        sortProducts(content, effectiveSortBy, effectiveSortDirection);

        productsPage = new PageImpl<>(content, pageable, productsPage.getTotalElements());

        List<ProductFrontendResponse> responses = content.stream()
                .map(product -> ozonService.toFrontendResponse(mapToProductInfo(product)))
                .collect(Collectors.toList());

        Map<String, Object> response = new HashMap<>();
        response.put("products", responses);
        response.put("currentPage", productsPage.getNumber());
        response.put("totalPages", productsPage.getTotalPages());
        response.put("totalElements", productsPage.getTotalElements());
        return ResponseEntity.ok(response);
    }

    private int calculateStock(OzonProduct product) {
        Map<String, Object> stocksMap = parseJson(product.getStocks(), new TypeReference<Map<String, Object>>() {});
        if (stocksMap == null || !stocksMap.containsKey("stocks")) {
            return 0;
        }
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> stocks = (List<Map<String, Object>>) stocksMap.get("stocks");
        return stocks.stream()
                .mapToInt(s -> s.containsKey("remaining") ? ((Number) s.get("remaining")).intValue() : 0)
                .sum();
    }
    // В OzonController.java добавить приватный метод для получения первого barcode:
    private String getFirstBarcode(OzonProduct product) {
        List<String> barcodes = parseJson(product.getBarcodes(), new TypeReference<List<String>>() {});
        return barcodes != null && !barcodes.isEmpty() ? barcodes.get(0) : "";
    }

    // В OzonController.java добавить приватный метод для получения первого tag:
    private String getFirstTag(OzonProduct product) {
        List<String> tags = parseJson(product.getTags(), new TypeReference<List<String>>() {});
        return tags != null && !tags.isEmpty() ? tags.get(0) : "";
    }

    // В OzonController.java добавить приватный метод для сортировки списка продуктов:
    private void sortProducts(List<OzonProduct> products, String sortBy, String sortDirection) {
        Comparator<OzonProduct> comparator;
        switch (sortBy) {
            case "name":
                comparator = Comparator.comparing(OzonProduct::getName, Comparator.nullsLast(Comparator.naturalOrder()));
                break;
            case "price":
                comparator = Comparator.comparing(
                        p -> p.getPrice() != null ? p.getPrice() : BigDecimal.ZERO,
                        Comparator.nullsLast(BigDecimal::compareTo)
                );
                break;
            case "sku":
                comparator = Comparator.comparingLong(p -> p.getSku() != null ? p.getSku() : 0L);
                break;
            case "offerId":
                comparator = Comparator.comparing(OzonProduct::getOfferId, Comparator.nullsLast(Comparator.naturalOrder()));
                break;
            case "stock":
                comparator = Comparator.comparingInt(this::calculateStock);
                break;
            case "barcode":
                comparator = Comparator.comparing(this::getFirstBarcode, Comparator.nullsLast(Comparator.naturalOrder()));
                break;
            case "tag":
                comparator = Comparator.comparing(this::getFirstTag, Comparator.nullsLast(Comparator.naturalOrder()));
                break;
            case "updatedAt":
                comparator = Comparator.comparing(OzonProduct::getUpdatedAt, Comparator.nullsLast(Comparator.naturalOrder()));
                break;
            default:
                return; // Нет сортировки если неизвестный sortBy
        }
        if ("DESC".equalsIgnoreCase(sortDirection)) {
            comparator = comparator.reversed();
        }
        products.sort(comparator);
    }
}