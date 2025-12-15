package org.ozonLabel.ozonApi.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ozonLabel.common.dto.ozon.*;
import org.ozonLabel.common.dto.user.UserResponseDto;
import org.ozonLabel.common.service.ozon.OzonService;
import org.ozonLabel.common.service.user.UserService;
import org.ozonLabel.ozonApi.repository.ProductFolderRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import org.ozonLabel.common.exception.ozon.OzonApiCredentialsMissingException;
import org.ozonLabel.common.exception.ozon.OzonApiException;
import org.ozonLabel.common.exception.ozon.UserNotFoundException;
import org.ozonLabel.ozonApi.entity.OzonProduct;
import org.ozonLabel.ozonApi.repository.OzonProductRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class OzonServiceIml implements OzonService {

    private static final String OZON_API_BASE_URL = "https://api-seller.ozon.ru";
    private static final String PRODUCT_LIST_ENDPOINT = "/v3/product/list";
    private static final String PRODUCT_INFO_ENDPOINT = "/v3/product/info/list";

    private final UserService userService;
    private final OzonProductRepository ozonProductRepository;
    private final ProductFolderRepository folderRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Синхронизация товаров с указанием папки
     */
    @Transactional
    public SyncProductsResponse syncProducts(Long userId, SyncProductsRequest request, Long folderId) {
        UserResponseDto user = userService.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("Пользователь с ID " + userId + " не найден"));

        log.info("ПЕРЕД validate: Class={} ozonClientId='{}' ozonApiKey='{}' hash={}",
                user.getClass().getName(),
                user.getOzonClientId(),
                user.getOzonApiKey(),
                System.identityHashCode(user));

        validateUserCredentials(user);

        // Проверяем существование папки, если указана
        if (folderId != null) {
            if (!folderRepository.existsByUserIdAndId(userId, folderId)) {
                throw new IllegalArgumentException("Папка не найдена");
            }
        }

        ProductListResponse productListResponse = getProductList(user, request);

        if (productListResponse.getResult() == null ||
                productListResponse.getResult().getItems() == null ||
                productListResponse.getResult().getItems().isEmpty()) {
            return SyncProductsResponse.builder()
                    .products(new ArrayList<>())
                    .total(0)
                    .message("Товары не найдены")
                    .build();
        }

        List<ProductListItem> items = productListResponse.getResult().getItems();
        List<ProductFrontendResponse> frontendResponses = new ArrayList<>();

        for (ProductListItem item : items) {
            try {
                ProductInfoResponse productInfoResponse = getProductInfo(user, item.getProductId());

                if (productInfoResponse.getItems() != null && !productInfoResponse.getItems().isEmpty()) {
                    ProductInfo productInfo = productInfoResponse.getItems().get(0);

                    // Сохраняем с указанием папки
                    OzonProduct savedProduct = saveProduct(userId, productInfo, folderId);

                    ProductFrontendResponse frontendResponse = mapToFrontendResponse(productInfo);
                    frontendResponses.add(frontendResponse);
                }
            } catch (Exception e) {
                log.error("Ошибка при обработке товара с product_id: {}", item.getProductId(), e);
            }
        }

        log.info("Синхронизировано {} товаров в папку {} для пользователя {}",
                frontendResponses.size(), folderId, userId);

        return SyncProductsResponse.builder()
                .products(frontendResponses)
                .total(productListResponse.getResult().getTotal())
                .message("Синхронизация завершена успешно")
                .build();
    }

    /**
     * Синхронизация товаров (старый метод без папки)
     */
    @Transactional
    public SyncProductsResponse syncProducts(Long userId, SyncProductsRequest request) {
        return syncProducts(userId, request, null);
    }

    private void validateUserCredentials(UserResponseDto user) {
        log.info("OzonClientId='{}', OzonApiKey='{}'", user.getOzonClientId(), user.getOzonApiKey());
        if (user.getOzonClientId() == null || user.getOzonClientId().trim().isEmpty() ||
                user.getOzonApiKey() == null || user.getOzonApiKey().trim().isEmpty()) {
            throw new OzonApiCredentialsMissingException(
                    "Необходимо указать Client-Id и Api-Key в настройках аккаунта. " +
                            "Перейдите в настройки и добавьте данные для интеграции с Ozon."
            );
        }
    }

    private ProductListResponse getProductList(UserResponseDto user, SyncProductsRequest request) {
        try {
            String url = OZON_API_BASE_URL + PRODUCT_LIST_ENDPOINT;

            ProductListRequest listRequest = ProductListRequest.builder()
                    .filter(request.getFilter() != null ? request.getFilter() : new HashMap<>())
                    .lastId(request.getLastId() != null ? request.getLastId() : "")
                    .limit(request.getLimit() != null ? request.getLimit() : 100)
                    .build();

            HttpHeaders headers = createHeaders(user);
            HttpEntity<ProductListRequest> entity = new HttpEntity<>(listRequest, headers);

            ResponseEntity<ProductListResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    ProductListResponse.class
            );

            return response.getBody();
        } catch (Exception e) {
            log.error("Ошибка при запросе списка товаров из Ozon API", e);
            throw new OzonApiException("Ошибка при получении списка товаров: " + e.getMessage(), e);
        }
    }

    private ProductInfoResponse getProductInfo(UserResponseDto user, Long productId) {
        try {
            String url = OZON_API_BASE_URL + PRODUCT_INFO_ENDPOINT;

            ProductInfoRequest infoRequest = ProductInfoRequest.builder()
                    .productId(Collections.singletonList(productId))
                    .build();

            HttpHeaders headers = createHeaders(user);
            HttpEntity<ProductInfoRequest> entity = new HttpEntity<>(infoRequest, headers);

            ResponseEntity<ProductInfoResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    ProductInfoResponse.class
            );

            return response.getBody();
        } catch (Exception e) {
            log.error("Ошибка при запросе информации о товаре {} из Ozon API", productId, e);
            throw new OzonApiException("Ошибка при получении информации о товаре: " + e.getMessage(), e);
        }
    }

    private HttpHeaders createHeaders(UserResponseDto user) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Client-Id", user.getOzonClientId());
        headers.set("Api-Key", user.getOzonApiKey());
        headers.set("Content-Type", "application/json");
        return headers;
    }

    @Transactional
    // В OzonServiceIml.java

    private OzonProduct saveProduct(Long userId, ProductInfo productInfo, Long folderId) {
        // Ozon возвращает product_id как String, но у тебя в БД он Long → парсим
        Long ozonProductId = productInfo.getId();

        // Ищем существующий товар по user_id + product_id (это уникальный ключ!)
        Optional<OzonProduct> existingOpt = ozonProductRepository
                .findByUserIdAndProductId(userId, ozonProductId);

        OzonProduct product = existingOpt.orElseGet(OzonProduct::new);

        // Если новый — устанавливаем обязательные поля
        if (existingOpt.isEmpty()) {
            product.setUserId(userId);
            product.setProductId(ozonProductId);
        }

        // Обязательно ставим папку (даже если null — значит "без папки")
        product.setFolderId(folderId);

        // Основные поля
        product.setName(productInfo.getName());
        product.setOfferId(productInfo.getOfferId());

        // Цены
        product.setPrice(toBigDecimal(productInfo.getPrice()));
        product.setOldPrice(toBigDecimal(productInfo.getOldPrice()));
        product.setMinPrice(toBigDecimal(productInfo.getMinPrice()));

        // Остальные поля из API
        product.setSku(productInfo.getSku() != null ? productInfo.getSku() : null);
        product.setCurrencyCode(productInfo.getCurrencyCode());

        product.setIsKgt(productInfo.getIsKgt());
        product.setIsPrepaymentAllowed(productInfo.getIsPrepaymentAllowed());
        product.setIsSuper(productInfo.getIsSuper());
        product.setIsSeasonal(productInfo.getIsSeasonal());

        // JSON поля — сохраняем как есть (ObjectMapper сам сделает toString → JSON)
        product.setBarcodes(toJson(productInfo.getBarcodes()));
        product.setImages(toJson(productInfo.getImages()));
        product.setSources(toJson(productInfo.getSources()));
        product.setModel_info(toJson(productInfo.getModelInfo()));
        product.setCommissions(toJson(productInfo.getCommissions()));
        product.setStocks(toJson(productInfo.getStocks()));
        product.setErrors(toJson(productInfo.getErrors()));
        product.setVisibility_details(toJson(productInfo.getVisibilityDetails()));
        product.setPrice_indexes(toJson(productInfo.getPriceIndexes()));
        product.setImages360(toJson(productInfo.getImages360()));
        product.setColor_image(toJson(productInfo.getColorImage()));
        product.setPrimary_image(toJson(productInfo.getPrimaryImage()));
        product.setStatuses(toJson(productInfo.getStatuses()));
        product.setPromotions(toJson(productInfo.getPromotions()));
        product.setAvailabilities(toJson(productInfo.getAvailabilities()));

        // Размер — попробуем вытащить из названия или артикула
        product.setSize(extractSize(productInfo.getName()));

        // Время обновления
        product.setUpdatedAt(LocalDateTime.now());

        try {
            return ozonProductRepository.save(product);
        } catch (Exception e) {
            log.error("Ошибка при сохранении товара product_id={} для пользователя {}", ozonProductId, userId, e);
            throw new RuntimeException("Не удалось сохранить товар: " + ozonProductId, e);
        }
    }

    public ProductFrontendResponse toFrontendResponse(OzonProduct product) {
        // Получаем первую картинку из массива images
        List<String> images = parseJson(product.getImages(), new TypeReference<List<String>>() {});
        String image = (images != null && !images.isEmpty()) ? images.get(0) : null;

        String priceStr = product.getPrice() != null ? product.getPrice().toString() : "0";
        Integer stock = calculateStock(product.getStocks());
        String color = product.getSize();
        List<String> tags = new ArrayList<>();
        String ozonArticle = product.getSku() != null ? product.getSku().toString() : product.getProductId().toString();
        String sellerArticle = product.getOfferId();

        // Статусы парсим как объект
        List<String> statuses = new ArrayList<>();
        try {
            Map<String, Object> statusData = parseJson(product.getStatuses(), new TypeReference<Map<String, Object>>() {});
            if (statusData != null) {
                Object statusName = statusData.get("status_name");
                if (statusName instanceof String) {
                    statuses.add((String) statusName);
                }
            }
        } catch (Exception e) {
            log.debug("Не удалось распарсить статусы как объект для товара {}", product.getId());
        }


        // Получаем color_index из price_indexes
        String colorIndex = null;
        Map<String, Object> priceIndexes = parseJson(product.getPrice_indexes(), new TypeReference<Map<String, Object>>() {});
        if (priceIndexes != null && priceIndexes.containsKey("color_index")) {
            colorIndex = (String) priceIndexes.get("color_index");
        }

        // ИСПРАВЛЕНИЕ: Получаем количество из model_info
        Integer modelCount = 0;
        if (product.getModel_info() != null && !product.getModel_info().isEmpty()) {
            try {
                // Парсим JSON строку model_info в Map
                Map<String, Object> modelInfo = objectMapper.readValue(
                        product.getModel_info(),
                        new TypeReference<Map<String, Object>>() {}
                );

                if (modelInfo != null && modelInfo.containsKey("count")) {
                    Object countObj = modelInfo.get("count");
                    if (countObj instanceof Integer) {
                        modelCount = (Integer) countObj;
                    } else if (countObj instanceof Number) {
                        modelCount = ((Number) countObj).intValue();
                    } else if (countObj instanceof String) {
                        try {
                            modelCount = Integer.parseInt((String) countObj);
                        } catch (NumberFormatException e) {
                            log.warn("Неверный формат count в model_info: {}", countObj);
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Ошибка при парсинге model_info JSON: {}", product.getModel_info(), e);
            }
        }

        return ProductFrontendResponse.builder()
                .image(image)
                .name(product.getName())
                .id(product.getProductId().toString())
                .price(priceStr)
                .sku(product.getSku())
                .offerId(product.getOfferId())
                .modelCount(modelCount)  // Теперь здесь будет правильное количество
                .statuses(statuses)
                .colorIndex(colorIndex)
                .barcode(product.getBarcodes())
                .ozonArticle(ozonArticle)
                .sellerArticle(sellerArticle)
                .stock(stock)
                .color(color)
                .tags(tags)
                .build();
    }

    public ProductFrontendResponse mapToFrontendResponse(ProductInfo productInfo) {
        String image = (productInfo.getImages() != null && !productInfo.getImages().isEmpty())
                ? productInfo.getImages().get(0)
                : null;

        // ИСПРАВЛЕНИЕ: Получаем количество из modelInfo
        Integer modelCount = 0;
        if (productInfo.getModelInfo() != null) {
            Object countObj = productInfo.getModelInfo().get("count");
            if (countObj instanceof Integer) {
                modelCount = (Integer) countObj;
            } else if (countObj instanceof Number) {
                modelCount = ((Number) countObj).intValue();
            } else if (countObj instanceof String) {
                try {
                    modelCount = Integer.parseInt((String) countObj);
                } catch (NumberFormatException e) {
                    log.warn("Неверный форма count в modelInfo: {}", countObj);
                }
            }
        }

        String colorIndex = null;
        if (productInfo.getPriceIndexes() != null &&
                productInfo.getPriceIndexes().containsKey("color_index")) {
            Object colorIndexObj = productInfo.getPriceIndexes().get("color_index");
            colorIndex = colorIndexObj != null ? colorIndexObj.toString() : null;
        }

        return ProductFrontendResponse.builder()
                .image(image)
                .name(productInfo.getName())
                .id(String.valueOf(productInfo.getId()))
                .price(parseBigDecimal(productInfo.getPrice()))
                .sku(productInfo.getSku())
                .offerId(productInfo.getOfferId())
                .modelCount(modelCount)  // Теперь здесь будет правильное количество
                .statuses((List<String>) productInfo.getStatuses())
                .colorIndex(colorIndex)
                .build();
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

    @Override
    public ProductFrontendResponse toFrontendResponse(ProductInfo product) {
        if (product == null) return null;
        String image = (product.getImages() != null && !product.getImages().isEmpty())
                ? product.getImages().get(0)
                : null;
        return ProductFrontendResponse.builder()
                .id(String.valueOf(product.getId()))
                .name(product.getName())
                .price(product.getPrice())
                .sku(product.getSku())
                .offerId(product.getOfferId())
                .image(image)
                .statuses(product.getStatuses() != null ? List.copyOf(product.getStatuses().keySet()) : List.of())
                .build();
    }

    @Override
    public boolean existsByUserIdAndProductId(Long userId, Long productId) {
        return ozonProductRepository.existsByUserIdAndProductId(userId, productId);
    }

    @Override
    public Optional<ProductInfo> findByUserIdAndProductId(Long userId, Long productId) {
        return ozonProductRepository.findByUserIdAndProductId(userId, productId)
                .map(this::mapToProductInfo);
    }

    @Override
    public List<ProductInfo> findByUserId(Long userId) {
        return ozonProductRepository.findByUserId(userId)
                .stream().map(this::mapToProductInfo).toList();
    }

    @Override
    public List<ProductInfo> findByUserIdOrderByUpdatedAtDesc(Long userId) {
        return ozonProductRepository.findByUserIdOrderByUpdatedAtDesc(userId)
                .stream().map(this::mapToProductInfo).toList();
    }

    @Override
    public Page<ProductInfo> findByUserIdOrderByUpdatedAtDesc(Long userId, Pageable pageable) {
        return ozonProductRepository.findByUserIdOrderByUpdatedAtDesc(userId, pageable)
                .map(this::mapToProductInfo);
    }

    @Override
    public void deleteByUserId(Long userId) {
        ozonProductRepository.deleteByUserId(userId);
    }

    @Override
    public Long countByUserId(Long userId) {
        return ozonProductRepository.countByUserId(userId);
    }

    @Override
    public List<ProductInfo> findByUserIdAndAssignedToUserId(Long userId, Long assignedToUserId) {
        return ozonProductRepository.findByUserIdAndAssignedToUserId(userId, assignedToUserId)
                .stream().map(this::mapToProductInfo).toList();
    }

    @Override
    public Page<ProductInfo> findByUserIdAndAssignedToUserId(Long userId, Long assignedToUserId, Pageable pageable) {
        return ozonProductRepository.findByUserIdAndAssignedToUserId(userId, assignedToUserId, pageable)
                .map(this::mapToProductInfo);
    }

    @Override
    public List<ProductInfo> findByUserIdAndAssignedToUserIdIsNull(Long userId) {
        return ozonProductRepository.findByUserIdAndAssignedToUserIdIsNull(userId)
                .stream().map(this::mapToProductInfo).toList();
    }

    @Override
    public Page<ProductInfo> findByUserIdAndAssignedToUserIdIsNull(Long userId, Pageable pageable) {
        return ozonProductRepository.findByUserIdAndAssignedToUserIdIsNull(userId, pageable)
                .map(this::mapToProductInfo);
    }

    @Override
    public Page<ProductInfo> findByUserIdAndNameContainingIgnoreCase(Long userId, String name, Pageable pageable) {
        return ozonProductRepository.findByUserIdAndNameContainingIgnoreCase(userId, name, pageable)
                .map(this::mapToProductInfo);
    }

    @Override
    public Page<ProductInfo> findByUserIdAndAssignedToUserIdAndNameContainingIgnoreCase(Long userId, Long assignedToUserId, String name, Pageable pageable) {
        return ozonProductRepository.findByUserIdAndAssignedToUserIdAndNameContainingIgnoreCase(userId, assignedToUserId, name, pageable)
                .map(this::mapToProductInfo);
    }

    @Override
    public Long countByUserIdAndAssignedToUserId(Long userId, Long assignedToUserId) {
        return ozonProductRepository.countByUserIdAndAssignedToUserId(userId, assignedToUserId);
    }

    @Override
    public Long countByUserIdAndAssignedToUserIdIsNull(Long userId) {
        return ozonProductRepository.countByUserIdAndAssignedToUserIdIsNull(userId);
    }

    @Override
    public int bulkAssignProducts(List<Long> productIds, Long companyOwnerId, Long assignedUserId) {
        return ozonProductRepository.bulkAssignProducts(productIds, companyOwnerId, assignedUserId);
    }

    @Override
    public int bulkMoveProductsToFolder(List<Long> productIds, Long userId, Long folderId) {
        return ozonProductRepository.bulkMoveProductsToFolder(productIds, userId, folderId);
    }

    @Override
    public List<ProductInfo> findByUserIdAndFolderId(Long userId, Long folderId) {
        return ozonProductRepository.findByUserIdAndFolderId(userId, folderId)
                .stream().map(this::mapToProductInfo).toList();
    }

    @Override
    public Page<ProductInfo> findByUserIdAndFolderId(Long userId, Long folderId, Pageable pageable) {
        return ozonProductRepository.findByUserIdAndFolderId(userId, folderId, pageable)
                .map(this::mapToProductInfo);
    }

    @Override
    public List<ProductInfo> findByUserIdAndFolderIdIsNull(Long userId) {
        return ozonProductRepository.findByUserIdAndFolderIdIsNull(userId)
                .stream().map(this::mapToProductInfo).toList();
    }

    @Override
    public Page<ProductInfo> findByUserIdAndFolderIdIsNull(Long userId, Pageable pageable) {
        return ozonProductRepository.findByUserIdAndFolderIdIsNull(userId, pageable)
                .map(this::mapToProductInfo);
    }

    @Override
    public Long countByUserIdAndFolderId(Long userId, Long folderId) {
        return ozonProductRepository.countByUserIdAndFolderId(userId, folderId);
    }

    @Override
    public Long countByUserIdAndFolderIdIsNull(Long userId) {
        return ozonProductRepository.countByUserIdAndFolderIdIsNull(userId);
    }

    @Override
    public List<ProductInfo> findByUserIdAndSize(Long userId, String size) {
        return ozonProductRepository.findByUserIdAndSize(userId, size)
                .stream().map(this::mapToProductInfo).toList();
    }

    @Override
    public Page<ProductInfo> findByUserIdAndSize(Long userId, String size, Pageable pageable) {
        return ozonProductRepository.findByUserIdAndSize(userId, size, pageable)
                .map(this::mapToProductInfo);
    }

    @Override
    @Transactional
    public ProductInfo saveProduct(ProductInfo productInfo) {
        OzonProduct entity = mapToOzonProduct(productInfo); // метод для маппинга DTO -> Entity
        entity = ozonProductRepository.save(entity);           // сохраняем в БД
        return mapToProductInfo(entity);                   // возвращаем DTO
    }

    private OzonProduct mapToOzonProduct(ProductInfo info) {
        if (info == null) {
            return null;
        }

        OzonProduct product = OzonProduct.builder()
                .productId(info.getId())
                .name(info.getName())
                .sku(info.getSku())
                .offerId(info.getOfferId())
                .isArchived(info.getIsArchived())
                .isAutoarchived(info.getIsAutoarchived())
                .price(info.getPrice() != null ? new BigDecimal(info.getPrice()) : null)
                .oldPrice(info.getOldPrice() != null ? new BigDecimal(info.getOldPrice()) : null)
                .minPrice(info.getMinPrice() != null ? new BigDecimal(info.getMinPrice()) : null)
                .currencyCode(info.getCurrencyCode())
                .volumeWeight(info.getVolumeWeight() != null ? BigDecimal.valueOf(info.getVolumeWeight()) : null)
                .discountedFboStocks(info.getDiscountedFboStocks())
                .isDiscounted(info.getIsDiscounted())
                .hasDiscountedFboItem(info.getHasDiscountedFboItem())
                .isKgt(info.getIsKgt())
                .isSuper(info.getIsSuper())
                .isSeasonal(info.getIsSeasonal())
                .vat(info.getVat() != null ? new BigDecimal(info.getVat()) : null)
                .createdAt(info.getCreatedAt() != null ? LocalDateTime.parse(info.getCreatedAt()) : LocalDateTime.now())
                .updatedAt(info.getUpdatedAt() != null ? LocalDateTime.parse(info.getUpdatedAt()) : LocalDateTime.now())
                // JSON-поля оставляем пустыми или можно сериализовать Map/List в JSON
                .barcodes(info.getBarcodes() != null ? serializeToJson(info.getBarcodes()) : null)
                .images(info.getImages() != null ? serializeToJson(info.getImages()) : null)
                .images360(info.getImages360() != null ? serializeToJson(info.getImages360()) : null)
                .color_image(info.getColorImage() != null ? serializeToJson(info.getColorImage()) : null)
                .primary_image(info.getPrimaryImage() != null ? serializeToJson(info.getPrimaryImage()) : null)
                .sources(info.getSources() != null ? serializeToJson(info.getSources()) : null)
                .model_info(info.getModelInfo() != null ? serializeToJson(info.getModelInfo()) : null)
                .commissions(info.getCommissions() != null ? serializeToJson(info.getCommissions()) : null)
                .stocks(info.getStocks() != null ? serializeToJson(info.getStocks()) : null)
                .errors(info.getErrors() != null ? serializeToJson(info.getErrors()) : null)
                .visibility_details(info.getVisibilityDetails() != null ? serializeToJson(info.getVisibilityDetails()) : null)
                .price_indexes(info.getPriceIndexes() != null ? serializeToJson(info.getPriceIndexes()) : null)
                .promotions(info.getPromotions() != null ? serializeToJson(info.getPromotions()) : null)
                .statuses(info.getStatuses() != null ? serializeToJson(info.getStatuses()) : null)
                .availabilities(info.getAvailabilities() != null ? serializeToJson(info.getAvailabilities()) : null)
                .build();

        return product;
    }

    // Вспомогательный метод сериализации в JSON
    private String serializeToJson(Object obj) {
        try {
            return new ObjectMapper().writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.error("Ошибка сериализации в JSON", e);
            return null;
        }
    }



    private BigDecimal toBigDecimal(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String toJson(Object obj) {
        if (obj == null) return null;
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.warn("Ошибка сериализации JSON", e);
            return null;
        }
    }

    // Удали старый метод parseJson и вставь этот вместо него
    @SuppressWarnings("unchecked")
    private <T> T parseJson(String json, Object type) {
        if (json == null || json.isEmpty()) return null;
        try {
            if (type instanceof Class<?>) {
                return (T) objectMapper.readValue(json, (Class<?>) type);
            } else if (type instanceof TypeReference<?>) {
                return objectMapper.readValue(json, (TypeReference<T>) type);
            }
            return null;
        } catch (Exception e) {
            log.warn("JSON parse error: {}", json, e);
            return null;
        }
    }

    private Integer calculateStock(String stocksJson) {
        try {
            // Парсим JSON как Map<String, Object>
            Map<String, Object> stocksData = parseJson(stocksJson, new TypeReference<Map<String, Object>>() {});
            if (stocksData == null) return 0;

            // Проверяем, есть ли поле stocks как массив
            Object stocksObj = stocksData.get("stocks");
            if (stocksObj instanceof List) {
                List<Map<String, Object>> stocksList = (List<Map<String, Object>>) stocksObj;
                int totalPresent = 0;
                int totalReserved = 0;

                for (Map<String, Object> stock : stocksList) {
                    Object present = stock.get("present");
                    Object reserved = stock.get("reserved");

                    if (present instanceof Number) {
                        totalPresent += ((Number) present).intValue();
                    }
                    if (reserved instanceof Number) {
                        totalReserved += ((Number) reserved).intValue();
                    }
                }

                return Math.max(0, totalPresent - totalReserved);
            }

            // Старая логика для обратной совместимости
            Integer present = (Integer) stocksData.getOrDefault("present", 0);
            Integer reserved = (Integer) stocksData.getOrDefault("reserved", 0);
            return Math.max(0, present - reserved);

        } catch (Exception e) {
            log.warn("Ошибка при расчете стока из JSON: {}", stocksJson, e);
            return 0;
        }
    }

    // Простой способ вытащить размер из названия (настрой под себя)
    private String extractSize(String name) {
        if (name == null) return null;
        // Примеры: "Футболка XL", "Наклейка 100x100", "Этикетка 50мм"
        var match = java.util.regex.Pattern.compile("(?:\\b|\\D)([XSML]|XX*L|\\d{1,3}[xхX]\\d{1,3}(?:mm|мм|см|cm)?|\\d{1,3}[xхX]\\d{1,3})\\b", java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(name);
        return match.find() ? match.group(1).toUpperCase() : null;
    }

    private String parseBigDecimal(String value) {
        try {
            return value != null && !value.isEmpty() ? String.valueOf(new BigDecimal(value)) : null;
        } catch (Exception e) {
            return null;
        }
    }

    private LocalDateTime parseDateTime(String dateTime) {
        try {
            if (dateTime == null || dateTime.isEmpty()) {
                return null;
            }
            DateTimeFormatter formatter = DateTimeFormatter.ISO_DATE_TIME;
            return LocalDateTime.parse(dateTime, formatter);
        } catch (Exception e) {
            log.error("Ошибка при парсинге даты: {}", dateTime, e);
            return null;
        }
    }
}