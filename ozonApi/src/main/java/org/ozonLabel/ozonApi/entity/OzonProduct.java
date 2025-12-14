package org.ozonLabel.ozonApi.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.ozonLabel.user.entity.User;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "ozon_products")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
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
    @JdbcTypeCode(SqlTypes.JSON)
    private String barcodes;

    @Column(name = "description_category_id")
    private Long descriptionCategoryId;

    @Column(name = "type_id")
    private Long typeId;

    @Column(name = "product_created_at")
    private LocalDateTime productCreatedAt;

    @Column(columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String images;

    @Column(name = "currency_code")
    private String currencyCode;

    @Column(name = "min_price")
    private BigDecimal minPrice;

    @Column(name = "old_price")
    private BigDecimal oldPrice;

    private BigDecimal price;

    @Column(columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String sources;

    @Column(columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String model_info;

    @Column(columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
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
    @JdbcTypeCode(SqlTypes.JSON)
    private String stocks;

    @Column(columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String errors;

    @Column(name = "product_updated_at")
    private LocalDateTime productUpdatedAt;

    private BigDecimal vat;

    @Column(columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String visibility_details;

    @Column(columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String price_indexes;

    @Column(columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String images360;

    @Column(name = "is_kgt")
    private Boolean isKgt;

    @Column(columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String color_image;

    @Column(columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String primary_image;

    @Column(columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String statuses;

    @Column(name = "is_super")
    private Boolean isSuper;

    @Column(name = "is_seasonal")
    private Boolean isSeasonal;

    @Column(columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String promotions;

    private Long sku;

    @Column(columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String availabilities;

    @Column(name = "folder_id")
    private Long folderId;

    @Column(name = "size", length = 100)
    private String size;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "assigned_to_user_id")
    private Long assignedToUserId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_to_user_id", insertable = false, updatable = false)
    private User assignedToUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private User owner;

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