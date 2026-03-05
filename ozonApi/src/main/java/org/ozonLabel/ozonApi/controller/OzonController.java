package org.ozonLabel.ozonApi.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ozonLabel.common.dto.ozon.*;
import org.ozonLabel.common.service.ozon.OzonService;
import org.ozonLabel.common.service.ozon.ProductCreationService;
import org.ozonLabel.common.service.user.CompanyService;
import org.ozonLabel.common.service.user.UserService;
import org.ozonLabel.common.dto.user.UserResponseDto;
import org.ozonLabel.common.dto.datamatrix.DataMatrixStatsDto;
import org.ozonLabel.ozonApi.entity.OzonProduct;
import org.ozonLabel.ozonApi.entity.TableColumnSettings;
import org.ozonLabel.ozonApi.repository.OzonProductRepository;
import org.ozonLabel.ozonApi.repository.TableColumnSettingsRepository;
import org.ozonLabel.ozonApi.repository.LabelRepository;
import org.ozonLabel.ozonApi.repository.DataMatrixCodeRepository;
import org.ozonLabel.ozonApi.service.OzonServiceIml;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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
    private final UserService userService;
    private final TableColumnSettingsRepository columnSettingsRepository;
    private final LabelRepository labelRepository;
    private final DataMatrixCodeRepository dataMatrixCodeRepository;
    private final ObjectMapper objectMapper;

    /**
     * Синхронизирует товары из Ozon API с возможностью указания папки
     * SECURITY: Added authorization check to prevent IDOR
     */
    @PostMapping("/sync/{companyOwnerId}")
    public ResponseEntity<SyncProductsResponse> syncProducts(
            @PathVariable Long companyOwnerId,
            @RequestParam(required = false) Long folderId,
            @RequestBody(required = false) SyncProductsRequest request,
            Authentication auth) {

        // SECURITY: Verify user has access to this company
        String userEmail = auth.getName();
        companyService.checkAccess(userEmail, companyOwnerId);

        log.info("Начало синхронизации товаров для компании: {} в папку: {} пользователем: {}",
                companyOwnerId, folderId, userEmail);

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
        if (request.getLimit() == null || request.getLimit() > 1000) {
            // SECURITY: Limit max items to prevent DoS
            request.setLimit(Math.min(request.getLimit() != null ? request.getLimit() : 100, 1000));
        }

        SyncProductsResponse response = ozonService.syncProducts(companyOwnerId, request, folderId);

        log.info("Синхронизация завершена. Обработано товаров: {}", response.getProducts().size());

        return ResponseEntity.ok(response);
    }

    /**
     * Альтернативная ручка с параметрами в query string
     * SECURITY: Added authorization check to prevent IDOR
     */
    @GetMapping("/sync/{companyOwnerId}")
    public ResponseEntity<SyncProductsResponse> syncProductsSimple(
            @PathVariable Long companyOwnerId,
            @RequestParam(required = false) Long folderId,
            @RequestParam(required = false, defaultValue = "") String lastId,
            @RequestParam(required = false, defaultValue = "100") Integer limit,
            Authentication auth) {

        SyncProductsRequest request = SyncProductsRequest.builder()
                .filter(new HashMap<>())
                .lastId(lastId)
                .limit(Math.min(limit, 1000)) // SECURITY: Limit max
                .build();

        return syncProducts(companyOwnerId, folderId, request, auth);
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
     * Создать товар из Excel файла (импорт товаров)
     * @deprecated Используйте /products/import-excel
     */
    @Deprecated
    @PostMapping("/products/upload-excel")
    public ResponseEntity<Map<String, Object>> uploadExcel(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) Long folderId,
            Authentication auth) {

        String userEmail = auth.getName();
        log.info("Загрузка Excel файла '{}' для пользователя {} (deprecated endpoint)",
                file.getOriginalFilename(), userEmail);

        // Для обратной совместимости вызываем importFromExcel и возвращаем первый товар
        Long companyOwnerId = getUserCompanyId(userEmail);
        ExcelImportResult result = productCreationService.importFromExcel(userEmail, companyOwnerId, file, folderId);

        if (result.getProductIds() == null || result.getProductIds().isEmpty()) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Файл не содержит данных");
            return ResponseEntity.badRequest().body(response);
        }

        Long firstProductId = result.getProductIds().get(0);
        OzonProduct product = productRepository.findByUserIdAndProductId(companyOwnerId, firstProductId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Товар не найден"));

        ProductInfo productInfo = mapToProductInfo(product);
        ProductFrontendResponse frontend = ozonService.toFrontendResponse(productInfo);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Файл успешно загружен");
        response.put("productId", firstProductId);
        response.put("filename", file.getOriginalFilename());
        response.put("product", frontend);
        response.put("importResult", result); // Добавляем полный результат импорта

        return ResponseEntity.ok(response);
    }

    /**
     * Импортировать товары из Excel файла
     */
    @PostMapping("/products/import-excel")
    public ResponseEntity<ExcelImportResult> importFromExcel(
            @RequestParam("file") MultipartFile file,
            @RequestParam Long companyOwnerId,
            @RequestParam(required = false) Long folderId,
            Authentication auth) {

        String userEmail = auth.getName();
        companyService.checkAccess(userEmail, companyOwnerId);

        log.info("Импорт товаров из Excel файла '{}' для пользователя {} в папку {}",
                file.getOriginalFilename(), userEmail, folderId);

        ExcelImportResult result = productCreationService.importFromExcel(userEmail, companyOwnerId, file, folderId);
        return ResponseEntity.ok(result);
    }

    /**
     * Обновить данные папки из Excel файла (обновление файла)
     */
    @PostMapping("/products/update-excel/{folderId}")
    public ResponseEntity<ExcelImportResult> updateExcelFile(
            @PathVariable Long folderId,
            @RequestParam Long companyOwnerId,
            @RequestParam("file") MultipartFile file,
            Authentication auth) {

        String userEmail = auth.getName();
        companyService.checkAccess(userEmail, companyOwnerId);

        log.info("Обновление папки {} из Excel файла '{}' для пользователя {}",
                folderId, file.getOriginalFilename(), userEmail);

        ExcelImportResult result = productCreationService.updateExcelFile(userEmail, companyOwnerId, folderId, file);
        return ResponseEntity.ok(result);
    }

    private Long getUserCompanyId(String userEmail) {
        // Получаем ID компании пользователя (в данном случае это userId)
        return userService.findByEmail(userEmail)
                .map(UserResponseDto::getId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Пользователь не найден"));
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
            throw new RuntimeException("Ошибка сериализации в JSON", e);
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

        // Получаем productId для загрузки hasLabel и dataMatrixStats
        List<Long> productIds = content.stream().map(OzonProduct::getProductId).toList();
        
        // Загружаем hasLabel одним запросом
        Set<Long> productIdsWithLabels = new HashSet<>(
            labelRepository.findProductIdsWithLabels(companyOwnerId, productIds)
        );
        
        // Загружаем dataMatrixStats одним запросом
        List<Object[]> dmStats = dataMatrixCodeRepository.getStatsByProductIds(companyOwnerId, productIds);
        Map<Long, DataMatrixStatsDto> statsMap = new HashMap<>();
        for (Object[] stat : dmStats) {
            Long pid = (Long) stat[0];
            Long total = ((Number) stat[1]).longValue();
            Long remaining = ((Number) stat[2]).longValue();
            Long used = total - remaining;
            statsMap.put(pid, DataMatrixStatsDto.builder()
                    .total(total)
                    .remaining(remaining)
                    .used(used)
                    .build());
        }

        List<ProductFrontendResponse> responses = content.stream()
                .map(product -> {
                    ProductFrontendResponse resp = ozonService.toFrontendResponse(mapToProductInfo(product));
                    resp.setPrintQuantity(product.getPrintQuantity() != null ? product.getPrintQuantity() : 1);
                    resp.setHasLabel(productIdsWithLabels.contains(product.getProductId()));
                    resp.setDataMatrixStats(statsMap.get(product.getProductId()));
                    return resp;
                })
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

        // Получаем productId для загрузки hasLabel и dataMatrixStats
        List<Long> productIds = content.stream().map(OzonProduct::getProductId).toList();
        
        // Загружаем hasLabel одним запросом
        Set<Long> productIdsWithLabels = new HashSet<>(
            labelRepository.findProductIdsWithLabels(companyOwnerId, productIds)
        );
        
        // Загружаем dataMatrixStats одним запросом
        List<Object[]> dmStats = dataMatrixCodeRepository.getStatsByProductIds(companyOwnerId, productIds);
        Map<Long, DataMatrixStatsDto> statsMap = new HashMap<>();
        for (Object[] stat : dmStats) {
            Long pid = (Long) stat[0];
            Long total = ((Number) stat[1]).longValue();
            Long remaining = ((Number) stat[2]).longValue();
            Long used = total - remaining;
            statsMap.put(pid, DataMatrixStatsDto.builder()
                    .total(total)
                    .remaining(remaining)
                    .used(used)
                    .build());
        }

        List<ProductFrontendResponse> responses = content.stream()
                .map(product -> {
                    ProductFrontendResponse resp = ozonService.toFrontendResponse(mapToProductInfo(product));
                    resp.setPrintQuantity(product.getPrintQuantity() != null ? product.getPrintQuantity() : 1);
                    resp.setHasLabel(productIdsWithLabels.contains(product.getProductId()));
                    resp.setDataMatrixStats(statsMap.get(product.getProductId()));
                    return resp;
                })
                .collect(Collectors.toList());

        Map<String, Object> response = new HashMap<>();
        response.put("products", responses);
        response.put("currentPage", productsPage.getNumber());
        response.put("totalPages", productsPage.getTotalPages());
        response.put("totalElements", productsPage.getTotalElements());
        return ResponseEntity.ok(response);
    }

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

        // Получаем productId для загрузки hasLabel и dataMatrixStats
        List<Long> productIds = content.stream().map(OzonProduct::getProductId).toList();
        
        // Загружаем hasLabel одним запросом
        Set<Long> productIdsWithLabels = new HashSet<>(
            labelRepository.findProductIdsWithLabels(companyOwnerId, productIds)
        );
        
        // Загружаем dataMatrixStats одним запросом
        List<Object[]> dmStats = dataMatrixCodeRepository.getStatsByProductIds(companyOwnerId, productIds);
        Map<Long, DataMatrixStatsDto> statsMap = new HashMap<>();
        for (Object[] stat : dmStats) {
            Long pid = (Long) stat[0];
            Long total = ((Number) stat[1]).longValue();
            Long remaining = ((Number) stat[2]).longValue();
            Long used = total - remaining;
            statsMap.put(pid, DataMatrixStatsDto.builder()
                    .total(total)
                    .remaining(remaining)
                    .used(used)
                    .build());
        }

        List<ProductFrontendResponse> responses = content.stream()
                .map(product -> {
                    ProductFrontendResponse resp = ozonService.toFrontendResponse(mapToProductInfo(product));
                    resp.setPrintQuantity(product.getPrintQuantity() != null ? product.getPrintQuantity() : 1);
                    resp.setHasLabel(productIdsWithLabels.contains(product.getProductId()));
                    resp.setDataMatrixStats(statsMap.get(product.getProductId()));
                    return resp;
                })
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

        // Получаем productId для загрузки hasLabel и dataMatrixStats
        List<Long> productIds = content.stream().map(OzonProduct::getProductId).toList();
        
        // Загружаем hasLabel одним запросом
        Set<Long> productIdsWithLabels = new HashSet<>(
            labelRepository.findProductIdsWithLabels(companyOwnerId, productIds)
        );
        
        // Загружаем dataMatrixStats одним запросом
        List<Object[]> dmStats = dataMatrixCodeRepository.getStatsByProductIds(companyOwnerId, productIds);
        Map<Long, DataMatrixStatsDto> statsMap = new HashMap<>();
        for (Object[] stat : dmStats) {
            Long pid = (Long) stat[0];
            Long total = ((Number) stat[1]).longValue();
            Long remaining = ((Number) stat[2]).longValue();
            Long used = total - remaining;
            statsMap.put(pid, DataMatrixStatsDto.builder()
                    .total(total)
                    .remaining(remaining)
                    .used(used)
                    .build());
        }

        List<ProductFrontendResponse> responses = content.stream()
                .map(product -> {
                    ProductFrontendResponse resp = ozonService.toFrontendResponse(mapToProductInfo(product));
                    resp.setPrintQuantity(product.getPrintQuantity() != null ? product.getPrintQuantity() : 1);
                    resp.setHasLabel(productIdsWithLabels.contains(product.getProductId()));
                    resp.setDataMatrixStats(statsMap.get(product.getProductId()));
                    return resp;
                })
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

    /**
     * Обновить количество копий для печати для одного товара
     */
    @PutMapping("/products/{productId}/quantity")
    public ResponseEntity<ProductFrontendResponse> updatePrintQuantity(
            @PathVariable Long productId,
            @RequestParam Long companyOwnerId,
            @RequestBody @Valid UpdateQuantityRequest request,
            Authentication auth) {

        String userEmail = auth.getName();
        companyService.checkAccess(userEmail, companyOwnerId);

        log.info("Обновление количества для печати товара {} для пользователя {}: quantity={}",
                productId, companyOwnerId, request.getQuantity());

        ProductInfo updated = ozonService.updatePrintQuantity(companyOwnerId, productId, request.getQuantity());
        return ResponseEntity.ok(ozonService.toFrontendResponse(updated));
    }

    /**
     * Массово обновить количество копий для печати
     */
    @PostMapping("/products/bulk/quantity")
    public ResponseEntity<Map<String, Object>> bulkUpdateQuantity(
            @RequestParam Long companyOwnerId,
            @RequestBody @Valid BulkUpdateQuantityRequest request,
            Authentication auth) {

        String userEmail = auth.getName();
        companyService.checkAccess(userEmail, companyOwnerId);

        if (request.getProductIds() == null || request.getProductIds().isEmpty()) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Список товаров не может быть пустым");
            return ResponseEntity.badRequest().body(errorResponse);
        }

        log.info("Массовое обновление количества для печати: {} товаров для пользователя {}",
                request.getProductIds().size(), companyOwnerId);

        int updatedCount = ozonService.bulkUpdateQuantity(request.getProductIds(), companyOwnerId, request.getQuantity());

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", String.format("Количество обновлено для %d товаров", updatedCount));
        response.put("updatedCount", updatedCount);
        response.put("totalRequested", request.getProductIds().size());

        return ResponseEntity.ok(response);
    }

    /**
     * Получить настройки видимости колонок таблицы
     */
    @GetMapping("/settings/columns")
    public ResponseEntity<Map<String, Boolean>> getColumnSettings(
            @RequestParam Long companyOwnerId,
            Authentication auth) {

        String userEmail = auth.getName();
        companyService.checkAccess(userEmail, companyOwnerId);

        log.info("Получение настроек видимости колонок для компании {}", companyOwnerId);

        Optional<TableColumnSettings> settingsOpt = columnSettingsRepository.findByCompanyId(companyOwnerId);
        
        Map<String, Boolean> defaultSettings = new HashMap<>();
        defaultSettings.put("photo", true);
        defaultSettings.put("barcode", true);
        defaultSettings.put("offerId", true);
        defaultSettings.put("name", true);
        defaultSettings.put("tags", true);      // всегда true
        defaultSettings.put("quantity", true);  // всегда true
        defaultSettings.put("price", true);
        defaultSettings.put("stock", true);
        defaultSettings.put("sku", true);

        if (settingsOpt.isEmpty()) {
            return ResponseEntity.ok(defaultSettings);
        }

        TableColumnSettings settings = settingsOpt.get();
        Map<String, Boolean> columns = parseJson(settings.getColumns(), new TypeReference<Map<String, Boolean>>() {});
        
        if (columns == null) {
            columns = new HashMap<>();
        }

        // Гарантируем что quantity и tags всегда true
        columns.put("quantity", true);
        columns.put("tags", true);

        return ResponseEntity.ok(columns);
    }

    /**
     * Обновить настройки видимости колонок таблицы
     */
    @PutMapping("/settings/columns")
    public ResponseEntity<Map<String, Boolean>> updateColumnSettings(
            @RequestParam Long companyOwnerId,
            @RequestBody Map<String, Boolean> columns,
            Authentication auth) {

        String userEmail = auth.getName();
        companyService.checkAccess(userEmail, companyOwnerId);

        log.info("Обновление настроек видимости колонок для компании {}", companyOwnerId);

        // quantity и tags всегда true, игнорируем попытки установить false
        if (columns == null) {
            columns = new HashMap<>();
        }
        columns.put("quantity", true);
        columns.put("tags", true);

        String columnsJson = serializeToJson(columns);
        
        Optional<TableColumnSettings> existingOpt = columnSettingsRepository.findByCompanyId(companyOwnerId);
        
        if (existingOpt.isPresent()) {
            TableColumnSettings existing = existingOpt.get();
            existing.setColumns(columnsJson);
            existing.setUpdatedAt(LocalDateTime.now());
            columnSettingsRepository.save(existing);
        } else {
            TableColumnSettings newSettings = TableColumnSettings.builder()
                    .companyId(companyOwnerId)
                    .columns(columnsJson)
                    .updatedAt(LocalDateTime.now())
                    .build();
            columnSettingsRepository.save(newSettings);
        }

        return ResponseEntity.ok(columns);
    }
}