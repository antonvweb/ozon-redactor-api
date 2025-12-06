package org.ozonLabel.domain.repository;

import org.ozonLabel.domain.model.ProductFolder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductFolderRepository extends JpaRepository<ProductFolder, Long> {

    // Получить все корневые папки пользователя
    List<ProductFolder> findByUserIdAndParentFolderIdIsNullOrderByPositionAsc(Long userId);

    // Получить все подпапки конкретной папки
    List<ProductFolder> findByUserIdAndParentFolderIdOrderByPositionAsc(Long userId, Long parentFolderId);

    // Получить все папки пользователя
    List<ProductFolder> findByUserIdOrderByPositionAsc(Long userId);

    // Найти папку по имени и родителю
    Optional<ProductFolder> findByUserIdAndNameAndParentFolderId(Long userId, String name, Long parentFolderId);

    // Проверить существование папки
    boolean existsByUserIdAndId(Long userId, Long folderId);

    // Получить дерево папок рекурсивно
    @Query(value = """
        WITH RECURSIVE folder_tree AS (
            SELECT id, user_id, parent_folder_id, name, color, icon, position, 
                   created_at, updated_at, 0 as level, 
                   CAST(name AS VARCHAR(1000)) as path
            FROM product_folders
            WHERE user_id = :userId AND parent_folder_id IS NULL
            
            UNION ALL
            
            SELECT f.id, f.user_id, f.parent_folder_id, f.name, f.color, f.icon, 
                   f.position, f.created_at, f.updated_at, ft.level + 1,
                   CAST(ft.path || ' > ' || f.name AS VARCHAR(1000))
            FROM product_folders f
            INNER JOIN folder_tree ft ON f.parent_folder_id = ft.id
            WHERE f.user_id = :userId
        )
        SELECT * FROM folder_tree ORDER BY path, position
        """, nativeQuery = true)
    List<Object[]> getFolderTreeForUser(@Param("userId") Long userId);

    // Получить путь к папке
    @Query(value = """
        WITH RECURSIVE folder_path AS (
            SELECT id, parent_folder_id, name, 0 as level
            FROM product_folders
            WHERE id = :folderId
            
            UNION ALL
            
            SELECT f.id, f.parent_folder_id, f.name, fp.level + 1
            FROM product_folders f
            INNER JOIN folder_path fp ON f.id = fp.parent_folder_id
        )
        SELECT name FROM folder_path ORDER BY level DESC
        """, nativeQuery = true)
    List<String> getFolderPath(@Param("folderId") Long folderId);

    // Подсчитать количество подпапок
    Long countByUserIdAndParentFolderId(Long userId, Long parentFolderId);

    // Получить все папки для удаления (папка + все вложенные)
    @Query(value = """
        WITH RECURSIVE folder_hierarchy AS (
            SELECT id FROM product_folders WHERE id = :folderId
            UNION ALL
            SELECT f.id FROM product_folders f
            INNER JOIN folder_hierarchy fh ON f.parent_folder_id = fh.id
        )
        SELECT id FROM folder_hierarchy
        """, nativeQuery = true)
    List<Long> getAllSubfolderIds(@Param("folderId") Long folderId);
}