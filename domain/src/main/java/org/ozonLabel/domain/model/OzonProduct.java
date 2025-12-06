package org.ozonLabel.domain.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "ozon_products")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OzonProduct {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    private String name;

    @Column(name = "offer_id")
    private String offerId;

    @Column(name = "is_archived")
    private Boolean isArchived;

    @Column(name = "is_autoarchived")
    private Boolean isAutoarchived;

    @Column(columnDefinition = "jsonb")
    private String barcodes;

    @Column(name = "description_category_id")
    private Long descriptionCategoryId;

    @Column(name = "type_id")
    private Long typeId;

    @Column(name = "product_created_at")
    private LocalDateTime productCreatedAt;

    @Column(columnDefinition = "jsonb")
    private String images;

    @Column(name = "currency_code")
    private String currencyCode;

    @Column(name = "min_price")
    private BigDecimal minPrice;

    @Column(name = "old_price")
    private BigDecimal oldPrice;

    private BigDecimal price;

    @Column(columnDefinition = "jsonb")
    private String sources;

    @Column(name = "model_info", columnDefinition = "jsonb")
    private String modelInfo;

    @Column(columnDefinition = "jsonb")
    private String commissions;

    @Column(name = "is_prepayment_allowed")
    private Boolean isPrepaymentAllowed;

    @Column(name = "volume_weight")
    private BigDecimal volumeWeight;

    @Column(name = "has_discounted_fbo_item")
    private Boolean hasDiscountedFboItem;

    @Column(name = "is_discounted")
    private Boolean isDiscounted;

    @Column(name = "discounted_fbo_stocks")
    private Integer discountedFboStocks;

    @Column(columnDefinition = "jsonb")
    private String stocks;

    @Column(columnDefinition = "jsonb")
    private String errors;

    @Column(name = "product_updated_at")
    private LocalDateTime productUpdatedAt;

    private BigDecimal vat;

    @Column(name = "visibility_details", columnDefinition = "jsonb")
    private String visibilityDetails;

    @Column(name = "price_indexes", columnDefinition = "jsonb")
    private String priceIndexes;

    @Column(columnDefinition = "jsonb")
    private String images360;

    @Column(name = "is_kgt")
    private Boolean isKgt;

    @Column(name = "color_image", columnDefinition = "jsonb")
    private String colorImage;

    @Column(name = "primary_image", columnDefinition = "jsonb")
    private String primaryImage;

    @Column(columnDefinition = "jsonb")
    private String statuses;

    @Column(name = "is_super")
    private Boolean isSuper;

    @Column(name = "is_seasonal")
    private Boolean isSeasonal;

    @Column(columnDefinition = "jsonb")
    private String promotions;

    private Long sku;

    @Column(columnDefinition = "jsonb")
    private String availabilities;

    @Column(name = "assigned_to_user_id")
    private Long assignedToUserId;

    @Column(name = "folder_id")
    private Long folderId;

    @Column(name = "size", length = 100)
    private String size;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private User owner;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_to_user_id", insertable = false, updatable = false)
    private User assignedToUser;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}