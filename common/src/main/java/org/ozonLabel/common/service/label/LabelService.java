package org.ozonLabel.common.service.label;

import org.ozonLabel.common.dto.label.CreateLabelDto;
import org.ozonLabel.common.dto.label.LabelResponseDto;
import org.ozonLabel.common.dto.label.UpdateLabelDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface LabelService {

    /**
     * Создать новую этикетку для товара
     */
    LabelResponseDto createLabel(String userEmail, CreateLabelDto dto);

    /**
     * Обновить существующую этикетку
     */
    LabelResponseDto updateLabel(String userEmail, Long productId, UpdateLabelDto dto);

    /**
     * Получить этикетку для товара
     */
    Optional<LabelResponseDto> getLabelByProductId(String userEmail, Long productId);

    /**
     * Получить все этикетки пользователя
     */
    List<LabelResponseDto> getAllLabels(String userEmail);

    /**
     * Получить все этикетки пользователя с пагинацией
     */
    Page<LabelResponseDto> getAllLabels(String userEmail, Pageable pageable);

    /**
     * Удалить этикетку
     */
    void deleteLabel(String userEmail, Long productId);

    /**
     * Проверить существование этикетки
     */
    boolean existsLabel(String userEmail, Long productId);

    /**
     * Поиск этикеток по имени
     */
    List<LabelResponseDto> searchLabelsByName(String userEmail, String name);
}