package org.ozonLabel.ozonApi.repository;

import org.ozonLabel.ozonApi.entity.Label;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LabelRepository extends JpaRepository<Label, Long> {

    // Поиск этикетки по пользователю и товару
    Optional<Label> findByUserIdAndProductId(Long userId, Long productId);

    // Проверка существования этикетки
    boolean existsByUserIdAndProductId(Long userId, Long productId);

    // Все этикетки пользователя
    List<Label> findByUserId(Long userId);

    // Все этикетки пользователя с пагинацией
    Page<Label> findByUserId(Long userId, Pageable pageable);

    // Этикетки пользователя, отсортированные по дате обновления
    @Query("SELECT l FROM Label l WHERE l.userId = :userId ORDER BY l.updatedAt DESC")
    List<Label> findByUserIdOrderByUpdatedAtDesc(@Param("userId") Long userId);

    // Количество этикеток у пользователя
    Long countByUserId(Long userId);

    // Удалить все этикетки пользователя
    void deleteByUserId(Long userId);

    // Удалить этикетку по пользователю и товару
    void deleteByUserIdAndProductId(Long userId, Long productId);

    // Поиск этикеток по имени
    @Query("SELECT l FROM Label l WHERE l.userId = :userId AND LOWER(l.name) LIKE LOWER(CONCAT('%', :name, '%'))")
    List<Label> searchByName(@Param("userId") Long userId, @Param("name") String name);
}