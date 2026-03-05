package org.ozonLabel.ozonApi.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "datamatrix_codes")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataMatrixCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "code", nullable = false, columnDefinition = "TEXT")
    private String code;

    @Column(name = "gtin", length = 14)
    private String gtin;

    @Column(name = "serial", length = 50)
    private String serial;

    @Column(name = "is_used", nullable = false)
    @Builder.Default
    private Boolean isUsed = false;

    @Column(name = "is_duplicate", nullable = false)
    @Builder.Default
    private Boolean isDuplicate = false;

    @Column(name = "used_at")
    private LocalDateTime usedAt;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "file_id")
    private Long fileId;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
