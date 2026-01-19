package org.ozonLabel.ozonApi.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "product_folders")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductFolder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "parent_folder_id")
    private Long parentFolderId;

    @Column(nullable = false)
    private String name;

    @Column(length = 20)
    private String color;

    @Column(length = 50)
    private String icon;

    @Column
    @Builder.Default
    private Integer position = 0;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_folder_id", insertable = false, updatable = false)
    private ProductFolder parentFolder;

    // Связь с подпапками
    @OneToMany(mappedBy = "parentFolder", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ProductFolder> subFolders = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Проверка, является ли папка корневой
    public boolean isRootFolder() {
        return parentFolderId == null;
    }

    // Получить путь к папке (для отображения breadcrumb)
    public String getPath() {
        if (isRootFolder()) {
            return name;
        }
        // Путь строится рекурсивно через запросы к БД
        return name;
    }
}