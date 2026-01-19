package org.ozonLabel.ozonApi.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ozonLabel.common.dto.label.CreateLabelDto;
import org.ozonLabel.common.dto.label.LabelConfigDto;
import org.ozonLabel.common.dto.label.LabelResponseDto;
import org.ozonLabel.common.dto.label.UpdateLabelDto;
import org.ozonLabel.common.dto.user.UserResponseDto;
import org.ozonLabel.common.service.label.LabelService;
import org.ozonLabel.common.service.user.UserService;
import org.ozonLabel.ozonApi.entity.Label;
import org.ozonLabel.ozonApi.repository.LabelRepository;
import org.ozonLabel.ozonApi.repository.OzonProductRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class LabelServiceImpl implements LabelService {

    private final LabelRepository labelRepository;
    private final OzonProductRepository productRepository;
    private final UserService userService;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public LabelResponseDto createLabel(String userEmail, CreateLabelDto dto) {
        UserResponseDto user = getUserByEmail(userEmail);

        // Проверяем существование товара
        validateProductExists(user.getId(), dto.getProductId());

        // Проверяем, нет ли уже этикетки для этого товара
        if (labelRepository.existsByUserIdAndProductId(user.getId(), dto.getProductId())) {
            throw new IllegalArgumentException("Этикетка для этого товара уже существует");
        }

        Label label = Label.builder()
                .userId(user.getId())
                .productId(dto.getProductId())
                .name(dto.getName())
                .config(serializeConfig(dto.getConfig()))
                .width(dto.getWidth() != null ? dto.getWidth() : new BigDecimal("58.00"))
                .height(dto.getHeight() != null ? dto.getHeight() : new BigDecimal("40.00"))
                .build();

        Label saved = labelRepository.save(label);

        log.info("Создана этикетка {} для товара {} пользователя {}",
                saved.getId(), dto.getProductId(), userEmail);

        return mapToResponseDto(saved);
    }

    @Override
    @Transactional
    public LabelResponseDto updateLabel(String userEmail, Long productId, UpdateLabelDto dto) {
        UserResponseDto user = getUserByEmail(userEmail);

        Label label = labelRepository.findByUserIdAndProductId(user.getId(), productId)
                .orElseThrow(() -> new IllegalArgumentException("Этикетка не найдена"));

        if (dto.getName() != null) {
            label.setName(dto.getName());
        }

        if (dto.getConfig() != null) {
            label.setConfig(serializeConfig(dto.getConfig()));
        }

        if (dto.getWidth() != null) {
            label.setWidth(dto.getWidth());
        }

        if (dto.getHeight() != null) {
            label.setHeight(dto.getHeight());
        }

        Label updated = labelRepository.save(label);

        log.info("Обновлена этикетка {} для товара {} пользователя {}",
                updated.getId(), productId, userEmail);

        return mapToResponseDto(updated);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<LabelResponseDto> getLabelByProductId(String userEmail, Long productId) {
        UserResponseDto user = getUserByEmail(userEmail);

        return labelRepository.findByUserIdAndProductId(user.getId(), productId)
                .map(this::mapToResponseDto);
    }

    @Override
    @Transactional(readOnly = true)
    public List<LabelResponseDto> getAllLabels(String userEmail) {
        UserResponseDto user = getUserByEmail(userEmail);

        return labelRepository.findByUserIdOrderByUpdatedAtDesc(user.getId())
                .stream()
                .map(this::mapToResponseDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<LabelResponseDto> getAllLabels(String userEmail, Pageable pageable) {
        UserResponseDto user = getUserByEmail(userEmail);

        return labelRepository.findByUserId(user.getId(), pageable)
                .map(this::mapToResponseDto);
    }

    @Override
    @Transactional
    public void deleteLabel(String userEmail, Long productId) {
        UserResponseDto user = getUserByEmail(userEmail);

        if (!labelRepository.existsByUserIdAndProductId(user.getId(), productId)) {
            throw new IllegalArgumentException("Этикетка не найдена");
        }

        labelRepository.deleteByUserIdAndProductId(user.getId(), productId);

        log.info("Удалена этикетка для товара {} пользователя {}", productId, userEmail);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsLabel(String userEmail, Long productId) {
        UserResponseDto user = getUserByEmail(userEmail);
        return labelRepository.existsByUserIdAndProductId(user.getId(), productId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<LabelResponseDto> searchLabelsByName(String userEmail, String name) {
        UserResponseDto user = getUserByEmail(userEmail);

        return labelRepository.searchByName(user.getId(), name)
                .stream()
                .map(this::mapToResponseDto)
                .collect(Collectors.toList());
    }

    // === Helper методы ===

    private UserResponseDto getUserByEmail(String email) {
        return userService.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден"));
    }

    private void validateProductExists(Long userId, Long productId) {
        if (!productRepository.existsByUserIdAndProductId(userId, productId)) {
            throw new IllegalArgumentException("Товар не найден");
        }
    }

    private String serializeConfig(LabelConfigDto config) {
        try {
            return objectMapper.writeValueAsString(config);
        } catch (JsonProcessingException e) {
            log.error("Ошибка сериализации конфигурации этикетки", e);
            throw new RuntimeException("Ошибка обработки конфигурации этикетки", e);
        }
    }

    private LabelConfigDto deserializeConfig(String configJson) {
        try {
            return objectMapper.readValue(configJson, LabelConfigDto.class);
        } catch (JsonProcessingException e) {
            log.error("Ошибка десериализации конфигурации этикетки", e);
            throw new RuntimeException("Ошибка обработки конфигурации этикетки", e);
        }
    }

    private LabelResponseDto mapToResponseDto(Label label) {
        return LabelResponseDto.builder()
                .id(label.getId())
                .userId(label.getUserId())
                .productId(label.getProductId())
                .name(label.getName())
                .config(deserializeConfig(label.getConfig()))
                .width(label.getWidth())
                .height(label.getHeight())
                .createdAt(label.getCreatedAt())
                .updatedAt(label.getUpdatedAt())
                .build();
    }
}