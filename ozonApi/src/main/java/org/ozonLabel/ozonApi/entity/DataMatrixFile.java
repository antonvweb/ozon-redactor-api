package org.ozonLabel.ozonApi.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "datamatrix_files")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataMatrixFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "uploaded_at", nullable = false)
    private LocalDateTime uploadedAt;

    @Column(name = "total_codes")
    private Integer totalCodes;

    @Column(name = "duplicate_count")
    private Integer duplicateCount;

    @Column(name = "original_content", columnDefinition = "TEXT")
    private String originalContent;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
