package org.ozonLabel.common.dto.ozon;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductInfo {
    private Long userId;
    private Long id;
    private String name;
    private Long folderId;       // ✅ добавлено
    private String size;
    @JsonProperty("offer_id")
    private String offerId;
    @JsonProperty("is_archived")
    private Boolean isArchived;
    @JsonProperty("is_autoarchived")
    private Boolean isAutoarchived;
    private List<String> barcodes;
    @JsonProperty("description_category_id")
    private Long descriptionCategoryId;
    @JsonProperty("type_id")
    private Long typeId;
    @JsonProperty("created_at")
    private String createdAt;
    private List<String> images;
    @JsonProperty("currency_code")
    private String currencyCode;
    @JsonProperty("min_price")
    private String minPrice;
    @JsonProperty("old_price")
    private String oldPrice;
    private String price;
    private List<Map<String, Object>> sources;
    @JsonProperty("model_info")
    private Map<String, Object> modelInfo;
    private List<Map<String, Object>> commissions;
    @JsonProperty("is_prepayment_allowed")
    private Boolean isPrepaymentAllowed;
    @JsonProperty("volume_weight")
    private Double volumeWeight;
    @JsonProperty("has_discounted_fbo_item")
    private Boolean hasDiscountedFboItem;
    @JsonProperty("is_discounted")
    private Boolean isDiscounted;
    @JsonProperty("discounted_fbo_stocks")
    private Integer discountedFboStocks;
    private Map<String, Object> stocks;
    private List<Map<String, Object>> errors;
    @JsonProperty("updated_at")
    private String updatedAt;
    private String vat;
    @JsonProperty("visibility_details")
    private Map<String, Object> visibilityDetails;
    @JsonProperty("price_indexes")
    private Map<String, Object> priceIndexes;
    private List<String> images360;
    @JsonProperty("is_kgt")
    private Boolean isKgt;
    @JsonProperty("color_image")
    private List<String> colorImage;
    @JsonProperty("primary_image")
    private List<String> primaryImage;
    private Map<String, Object> statuses;
    @JsonProperty("is_super")
    private Boolean isSuper;
    @JsonProperty("is_seasonal")
    private Boolean isSeasonal;
    private List<Map<String, Object>> promotions;
    private Long sku;
    private List<Map<String, Object>> availabilities;
}
