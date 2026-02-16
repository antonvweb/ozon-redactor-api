package org.ozonLabel.user.service;

import org.ozonLabel.common.dto.labelsize.*;
import org.ozonLabel.user.entity.LabelSize;
import org.ozonLabel.user.repository.LabelSizeRepository;
import org.ozonLabel.common.exception.user.ResourceNotFoundException;
import org.ozonLabel.common.exception.user.BadRequestException;
import org.ozonLabel.common.exception.user.AccessDeniedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class LabelSizeService {

    private final LabelSizeRepository labelSizeRepository;

    /**
     * Получить все шаблоны размеров для компании (включая системные)
     */
    @Transactional(readOnly = true)
    public List<LabelSizeResponse> getAllByCompany(Long companyId) {
        log.info("Получение всех шаблонов размеров для компании: {}", companyId);

        return labelSizeRepository.findAllByCompanyIdOrSystem(companyId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * Создать новый шаблон размера
     */
    @Transactional
    public LabelSizeResponse create(Long companyId, CreateLabelSizeRequest request) {
        log.info("Создание шаблона размера для компании: {}, название: {}", companyId, request.getName());

        // Проверка на дубликат имени
        if (labelSizeRepository.existsByCompanyIdAndNameIgnoreCase(companyId, request.getName())) {
            throw new BadRequestException("Шаблон с названием '" + request.getName() + "' уже существует");
        }

        LabelSize labelSize = new LabelSize();
        labelSize.setCompanyId(companyId);
        labelSize.setName(request.getName());
        labelSize.setWidth(request.getWidth());
        labelSize.setHeight(request.getHeight());
        labelSize.setIsDefault(request.getIsDefault() != null && request.getIsDefault());
        labelSize.setIsSystem(false);

        // Если новый шаблон по умолчанию, сбросить предыдущий
        if (Boolean.TRUE.equals(labelSize.getIsDefault())) {
            labelSizeRepository.resetDefaultForCompany(companyId);
        }

        LabelSize saved = labelSizeRepository.save(labelSize);
        log.info("Шаблон размера создан с id: {}", saved.getId());

        return toResponse(saved);
    }

    /**
     * Обновить шаблон размера
     */
    @Transactional
    public LabelSizeResponse update(Long id, Long companyId, UpdateLabelSizeRequest request) {
        log.info("Обновление шаблона размера id: {} для компании: {}", id, companyId);

        LabelSize labelSize = labelSizeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Шаблон размера с id=" + id + " не найден"));

        // Проверка принадлежности к компании
        if (!labelSize.getCompanyId().equals(companyId)) {
            throw new AccessDeniedException("Нет доступа к шаблону другой компании");
        }

        // Нельзя редактировать системные шаблоны
        if (Boolean.TRUE.equals(labelSize.getIsSystem())) {
            throw new BadRequestException("Системные шаблоны нельзя редактировать");
        }

        // Проверка на дубликат имени (если меняется)
        if (request.getName() != null && !request.getName().equalsIgnoreCase(labelSize.getName())) {
            if (labelSizeRepository.existsByCompanyIdAndNameIgnoreCase(companyId, request.getName())) {
                throw new BadRequestException("Шаблон с названием '" + request.getName() + "' уже существует");
            }
            labelSize.setName(request.getName());
        }

        if (request.getWidth() != null) {
            labelSize.setWidth(request.getWidth());
        }
        if (request.getHeight() != null) {
            labelSize.setHeight(request.getHeight());
        }

        // Обработка флага isDefault
        if (request.getIsDefault() != null && request.getIsDefault() && !Boolean.TRUE.equals(labelSize.getIsDefault())) {
            labelSizeRepository.resetDefaultForCompany(companyId);
            labelSize.setIsDefault(true);
        } else if (request.getIsDefault() != null && !request.getIsDefault()) {
            labelSize.setIsDefault(false);
        }

        LabelSize saved = labelSizeRepository.save(labelSize);
        log.info("Шаблон размера обновлен: {}", saved.getId());

        return toResponse(saved);
    }

    /**
     * Удалить шаблон размера
     */
    @Transactional
    public void delete(Long id, Long companyId) {
        log.info("Удаление шаблона размера id: {} для компании: {}", id, companyId);

        LabelSize labelSize = labelSizeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Шаблон размера с id=" + id + " не найден"));

        // Проверка принадлежности к компании
        if (!labelSize.getCompanyId().equals(companyId)) {
            throw new AccessDeniedException("Нет доступа к шаблону другой компании");
        }

        // Нельзя удалять системные шаблоны
        if (Boolean.TRUE.equals(labelSize.getIsSystem())) {
            throw new BadRequestException("Системные шаблоны нельзя удалять");
        }

        labelSizeRepository.delete(labelSize);
        log.info("Шаблон размера удален: {}", id);
    }

    /**
     * Преобразовать Entity в Response DTO
     */
    private LabelSizeResponse toResponse(LabelSize entity) {
        return LabelSizeResponse.builder()
                .id(entity.getId())
                .companyId(entity.getCompanyId())
                .name(entity.getName())
                .width(entity.getWidth())
                .height(entity.getHeight())
                .isDefault(entity.getIsDefault())
                .isSystem(entity.getIsSystem())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
