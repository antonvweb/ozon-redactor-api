package org.ozonLabel.ozonApi.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ozonLabel.domain.repository.ProductFolderRepository;
import org.ozonLabel.ozonApi.dto.*;
import org.ozonLabel.ozonApi.exception.OzonApiCredentialsMissingException;
import org.ozonLabel.ozonApi.exception.OzonApiException;
import org.ozonLabel.ozonApi.exception.UserNotFoundException;
import org.ozonLabel.domain.model.OzonProduct;
import org.ozonLabel.domain.model.User;
import org.ozonLabel.domain.repository.OzonProductRepository;
import org.ozonLabel.domain.repository.UserRepository;
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
public class OzonService {

    private static final String OZON_API_BASE_URL = "https://api-seller.ozon.ru";
    private static final String PRODUCT_LIST_ENDPOINT = "/v3/product/list";
    private static final String PRODUCT_INFO_ENDPOINT = "/v3/product/info/list";

    private final UserRepository userRepository;
    private final OzonProductRepository ozonProductRepository;
    private final ProductFolderRepository folderRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Синхронизация товаров с указанием папки
     */
    @Transactional
    public SyncProductsResponse syncProducts(Long userId, SyncProductsRequest request, Long folderId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("Пользователь с ID " + userId + " не найден"));

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

    private void validateUserCredentials(User user) {
        if (user.getOzonClientId() == null || user.getOzonClientId().trim().isEmpty() ||
                user.getOzonApiKey() == null || user.getOzonApiKey().trim().isEmpty()) {
            throw new OzonApiCredentialsMissingException(
                    "Необходимо указать Client-Id и Api-Key в настройках аккаунта. " +
                            "Перейдите в настройки и добавьте данные для интеграции с Ozon."
            );
        }
    }

    private ProductListResponse getProductList(User user, SyncProductsRequest request) {
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

    private ProductInfoResponse getProductInfo(User user, Long productId) {
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

    private HttpHeaders createHeaders(User user) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Client-Id", user.getOzonClientId());
        headers.set("Api-Key", user.getOzonApiKey());
        headers.set("Content-Type", "application/json");
        return headers;
    }

    @Transactional
    // В OzonService.java

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

    private ProductFrontendResponse mapToFrontendResponse(ProductInfo productInfo) {
        String image = (productInfo.getImages() != null && !productInfo.getImages().isEmpty())
                ? productInfo.getImages().get(0)
                : null;

        Integer modelCount = null;
        if (productInfo.getModelInfo() != null && productInfo.getModelInfo().containsKey("count")) {
            Object countObj = productInfo.getModelInfo().get("count");
            modelCount = countObj instanceof Integer ? (Integer) countObj : null;
        }

        String colorIndex = null;
        if (productInfo.getPriceIndexes() != null &&
                productInfo.getPriceIndexes().containsKey("color_index")) {
            colorIndex = (String) productInfo.getPriceIndexes().get("color_index");
        }

        return ProductFrontendResponse.builder()
                .image(image)
                .name(productInfo.getName())
                .id(productInfo.getId())
                .price(parseBigDecimal(productInfo.getPrice()))
                .sku(productInfo.getSku())
                .offerId(productInfo.getOfferId())
                .modelCount(modelCount)
                .statuses(productInfo.getStatuses())
                .colorIndex(colorIndex)
                .build();
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

    // Простой способ вытащить размер из названия (настрой под себя)
    private String extractSize(String name) {
        if (name == null) return null;
        // Примеры: "Футболка XL", "Наклейка 100x100", "Этикетка 50мм"
        var match = java.util.regex.Pattern.compile("(?:\\b|\\D)([XSML]|XX*L|\\d{1,3}[xхX]\\d{1,3}(?:mm|мм|см|cm)?|\\d{1,3}[xхX]\\d{1,3})\\b", java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(name);
        return match.find() ? match.group(1).toUpperCase() : null;
    }

    private BigDecimal parseBigDecimal(String value) {
        try {
            return value != null && !value.isEmpty() ? new BigDecimal(value) : null;
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