package org.ozonLabel.ozonApi.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ozonLabel.common.dto.label.CreateLabelDto;
import org.ozonLabel.common.dto.label.LabelConfigDto;
import org.ozonLabel.common.dto.label.LabelResponseDto;
import org.ozonLabel.common.dto.label.UpdateLabelDto;

import java.util.List;
import org.ozonLabel.common.dto.user.UserResponseDto;
import org.ozonLabel.common.exception.ozon.UserNotFoundException;
import org.ozonLabel.common.exception.user.ConflictException;
import org.ozonLabel.common.exception.user.ResourceNotFoundException;
import org.ozonLabel.common.service.label.LabelService;
import org.ozonLabel.common.service.user.CompanyService;
import org.ozonLabel.common.service.user.UserService;
import org.ozonLabel.ozonApi.entity.Label;
import org.ozonLabel.ozonApi.mapper.LabelMapper;
import org.ozonLabel.ozonApi.repository.LabelRepository;
import org.ozonLabel.ozonApi.validation.LabelValidator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class LabelServiceImpl implements LabelService {

    private final LabelRepository labelRepository;
    private final LabelMapper labelMapper;
    private final LabelValidator labelValidator;
    private final ObjectMapper objectMapper;
    private final CompanyService companyService;
    private final UserService userService;

    @Override
    @Transactional
    public LabelResponseDto createLabel(String userEmail, Long companyOwnerId, CreateLabelDto dto) {
        companyService.checkAccess(userEmail, companyOwnerId);
        Long userId = getUserIdByEmail(userEmail);

        labelValidator.validate(dto.getConfig());

        if (labelRepository.existsByCompanyIdAndProductId(companyOwnerId, dto.getProductId())) {
            throw new ConflictException("Этикетка для этого продукта уже существует");
        }

        Label label = Label.builder()
                .userId(userId)
                .companyId(companyOwnerId)
                .productId(dto.getProductId())
                .name(dto.getName())
                .width(dto.getConfig().getWidth())
                .height(dto.getConfig().getHeight())
                .unit(dto.getConfig().getUnit() != null ? dto.getConfig().getUnit() : "mm")
                .config(toJson(dto.getConfig()))
                .build();

        Label saved = labelRepository.save(label);
        log.info("Создана этикетка id={} для productId={} пользователем {}", saved.getId(), dto.getProductId(), userEmail);
        return labelMapper.toDto(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public LabelResponseDto getLabel(String userEmail, Long companyOwnerId, Long id) {
        companyService.checkAccess(userEmail, companyOwnerId);

        Label label = labelRepository.findByIdAndCompanyId(id, companyOwnerId)
                .orElseThrow(() -> new ResourceNotFoundException("Этикетка с id=" + id));
        return labelMapper.toDto(label);
    }

    @Override
    @Transactional(readOnly = true)
    public LabelResponseDto getLabelByProductId(String userEmail, Long companyOwnerId, Long productId) {
        companyService.checkAccess(userEmail, companyOwnerId);

        Label label = labelRepository.findByCompanyIdAndProductId(companyOwnerId, productId)
                .orElseThrow(() -> new ResourceNotFoundException("Этикетка для продукта с id=" + productId));
        return labelMapper.toDto(label);
    }

    @Override
    @Transactional
    public LabelResponseDto updateLabel(String userEmail, Long companyOwnerId, Long id, UpdateLabelDto dto) {
        companyService.checkAccess(userEmail, companyOwnerId);

        Label label = labelRepository.findByIdAndCompanyId(id, companyOwnerId)
                .orElseThrow(() -> new ResourceNotFoundException("Этикетка с id=" + id));

        labelValidator.validate(dto.getConfig());

        label.setName(dto.getName());
        label.setWidth(dto.getConfig().getWidth());
        label.setHeight(dto.getConfig().getHeight());
        label.setConfig(toJson(dto.getConfig()));

        Label saved = labelRepository.save(label);
        log.info("Обновлена этикетка id={} пользователем {}", saved.getId(), userEmail);
        return labelMapper.toDto(saved);
    }

    @Override
    @Transactional
    public void deleteLabel(String userEmail, Long companyOwnerId, Long id) {
        companyService.checkAccess(userEmail, companyOwnerId);

        Label label = labelRepository.findByIdAndCompanyId(id, companyOwnerId)
                .orElseThrow(() -> new ResourceNotFoundException("Этикетка с id=" + id));

        labelRepository.delete(label);
        log.info("Удалена этикетка id={} пользователем {}", id, userEmail);
    }

    @Override
    @Transactional
    public LabelResponseDto duplicateLabel(String userEmail, Long companyOwnerId, Long id, Long targetProductId) {
        companyService.checkAccess(userEmail, companyOwnerId);
        Long userId = getUserIdByEmail(userEmail);

        Label sourceLabel = labelRepository.findByIdAndCompanyId(id, companyOwnerId)
                .orElseThrow(() -> new ResourceNotFoundException("Этикетка с id=" + id));

        if (labelRepository.existsByCompanyIdAndProductId(companyOwnerId, targetProductId)) {
            throw new ConflictException("Этикетка для целевого продукта уже существует");
        }

        Label newLabel = Label.builder()
                .userId(userId)
                .companyId(companyOwnerId)
                .productId(targetProductId)
                .name(sourceLabel.getName())
                .width(sourceLabel.getWidth())
                .height(sourceLabel.getHeight())
                .unit(sourceLabel.getUnit())
                .config(sourceLabel.getConfig())
                .build();

        Label saved = labelRepository.save(newLabel);
        log.info("Дублирована этикетка id={} в новую id={} для productId={} пользователем {}",
                id, saved.getId(), targetProductId, userEmail);
        return labelMapper.toDto(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<LabelResponseDto> getLabelsByProductIds(String userEmail, Long companyOwnerId, List<Long> productIds) {
        companyService.checkAccess(userEmail, companyOwnerId);

        List<Label> labels = labelRepository.findByCompanyIdAndProductIdIn(companyOwnerId, productIds);
        log.info("Получено {} этикеток для {} продуктов компании {} пользователем {}",
                labels.size(), productIds.size(), companyOwnerId, userEmail);
        return labels.stream()
                .map(labelMapper::toDto)
                .toList();
    }

    private Long getUserIdByEmail(String email) {
        UserResponseDto user = userService.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("Пользователь с email " + email + " не найден"));
        return user.getId();
    }

    private String toJson(LabelConfigDto config) {
        try {
            return objectMapper.writeValueAsString(config);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Ошибка сериализации конфигурации этикетки", e);
        }
    }
}
