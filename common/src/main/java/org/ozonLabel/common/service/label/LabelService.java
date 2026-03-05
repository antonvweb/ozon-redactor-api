package org.ozonLabel.common.service.label;

import org.ozonLabel.common.dto.label.CreateLabelDto;
import org.ozonLabel.common.dto.label.DuplicateElementResponse;
import org.ozonLabel.common.dto.label.LabelResponseDto;
import org.ozonLabel.common.dto.label.ResizeLabelDto;
import org.ozonLabel.common.dto.label.UpdateLabelDto;

import java.util.List;

public interface LabelService {

    LabelResponseDto createLabel(String userEmail, Long companyOwnerId, CreateLabelDto dto);

    LabelResponseDto getLabel(String userEmail, Long companyOwnerId, Long id);

    LabelResponseDto getLabelByProductId(String userEmail, Long companyOwnerId, Long productId);

    LabelResponseDto updateLabel(String userEmail, Long companyOwnerId, Long id, UpdateLabelDto dto);

    void deleteLabel(String userEmail, Long companyOwnerId, Long id);

    LabelResponseDto duplicateLabel(String userEmail, Long companyOwnerId, Long id, Long targetProductId);

    List<LabelResponseDto> getLabelsByProductIds(String userEmail, Long companyOwnerId, List<Long> productIds);

    /**
     * Скопировать элемент с исходной этикетки на все этикетки продуктов указанной папки.
     * Подпапки НЕ обходятся.
     * @return количество обновлённых этикеток
     */
    DuplicateElementResponse duplicateElementToFolder(
            String userEmail,
            Long companyOwnerId,
            Long sourceLabelId,
            String elementId,
            Long folderId
    );

    /**
     * Изменить размер этикетки с опциональным пропорциональным масштабированием элементов.
     * @param dto autoFit true → пересчитать x, y, width, height всех элементов пропорционально
     */
    LabelResponseDto resizeLabelWithReposition(
            String userEmail,
            Long companyOwnerId,
            Long labelId,
            ResizeLabelDto dto
    );
}
