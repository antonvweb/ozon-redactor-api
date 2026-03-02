package org.ozonLabel.ozonApi.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ozonLabel.common.dto.label.CreateLabelDto;
import org.ozonLabel.common.dto.label.LabelConfigDto;
import org.ozonLabel.common.dto.label.LabelResponseDto;
import org.ozonLabel.common.dto.template.CreateLabelTemplateDto;
import org.ozonLabel.common.dto.template.LabelTemplateDto;
import org.ozonLabel.common.dto.template.UpdateLabelTemplateDto;
import org.ozonLabel.common.exception.user.ForbiddenException;
import org.ozonLabel.common.exception.user.ResourceNotFoundException;
import org.ozonLabel.common.exception.user.ValidationException;
import org.ozonLabel.common.service.label.LabelService;
import org.ozonLabel.common.service.template.LabelTemplateService;
import org.ozonLabel.common.service.user.CompanyService;
import org.ozonLabel.ozonApi.entity.LabelTemplate;
import org.ozonLabel.ozonApi.repository.LabelTemplateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class LabelTemplateServiceImpl implements LabelTemplateService {

    private final LabelTemplateRepository labelTemplateRepository;
    private final CompanyService companyService;
    private final LabelService labelService;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(readOnly = true)
    public List<LabelTemplateDto> getAllTemplates(String userEmail, Long companyOwnerId) {
        companyService.checkAccess(userEmail, companyOwnerId);
        
        List<LabelTemplate> templates = labelTemplateRepository.findByCompanyIdOrSystem(companyOwnerId);
        log.info("Получено {} шаблонов для компании {} пользователем {}", 
                templates.size(), companyOwnerId, userEmail);
        return templates.stream()
                .map(this::mapToDto)
                .toList();
    }

    @Override
    @Transactional
    public LabelTemplateDto createTemplate(String userEmail, Long companyOwnerId, CreateLabelTemplateDto dto) {
        companyService.checkAccess(userEmail, companyOwnerId);
        
        // Валидация конфигурации
        try {
            objectMapper.readValue(dto.getConfig(), LabelConfigDto.class);
        } catch (JsonProcessingException e) {
            throw new ValidationException("Неверный формат конфигурации этикетки: " + e.getMessage());
        }
        
        LabelTemplate template = LabelTemplate.builder()
                .name(dto.getName())
                .isSystem(false)
                .companyId(companyOwnerId)
                .userId(companyOwnerId)
                .width(dto.getWidth())
                .height(dto.getHeight())
                .unit(dto.getUnit() != null ? dto.getUnit() : "mm")
                .config(dto.getConfig())
                .previewUrl(dto.getPreviewUrl())
                .build();
        
        template = labelTemplateRepository.save(template);
        log.info("Создан шаблон id={} для компании {} пользователем {}", 
                template.getId(), companyOwnerId, userEmail);
        return mapToDto(template);
    }

    @Override
    @Transactional
    public LabelTemplateDto updateTemplate(String userEmail, Long companyOwnerId, Long id, UpdateLabelTemplateDto dto) {
        companyService.checkAccess(userEmail, companyOwnerId);
        
        LabelTemplate template = labelTemplateRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Шаблон с id=" + id + " не найден"));
        
        // Проверка: нельзя редактировать системные шаблоны
        if (template.getIsSystem()) {
            throw new ForbiddenException("Нельзя редактировать системный шаблон");
        }
        
        // Проверка принадлежности компании
        if (!template.getCompanyId().equals(companyOwnerId)) {
            throw new ForbiddenException("Доступ запрещён");
        }
        
        // Обновление полей
        if (dto.getName() != null) template.setName(dto.getName());
        if (dto.getWidth() != null) template.setWidth(dto.getWidth());
        if (dto.getHeight() != null) template.setHeight(dto.getHeight());
        if (dto.getUnit() != null) template.setUnit(dto.getUnit());
        if (dto.getConfig() != null) {
            // Валидация конфигурации
            try {
                objectMapper.readValue(dto.getConfig(), LabelConfigDto.class);
            } catch (JsonProcessingException e) {
                throw new ValidationException("Неверный формат конфигурации этикетки: " + e.getMessage());
            }
            template.setConfig(dto.getConfig());
        }
        if (dto.getPreviewUrl() != null) template.setPreviewUrl(dto.getPreviewUrl());
        
        template = labelTemplateRepository.save(template);
        log.info("Обновлён шаблон id={} для компании {} пользователем {}", 
                template.getId(), companyOwnerId, userEmail);
        return mapToDto(template);
    }

    @Override
    @Transactional
    public void deleteTemplate(String userEmail, Long companyOwnerId, Long id) {
        companyService.checkAccess(userEmail, companyOwnerId);
        
        LabelTemplate template = labelTemplateRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Шаблон с id=" + id + " не найден"));
        
        // Проверка: нельзя удалять системные шаблоны
        if (template.getIsSystem()) {
            throw new ForbiddenException("Нельзя удалить системный шаблон");
        }
        
        // Проверка принадлежности компании
        if (!template.getCompanyId().equals(companyOwnerId)) {
            throw new ForbiddenException("Доступ запрещён");
        }
        
        labelTemplateRepository.delete(template);
        log.info("Удалён шаблон id={} для компании {} пользователем {}", 
                template.getId(), companyOwnerId, userEmail);
    }

    @Override
    @Transactional
    public LabelTemplateDto applyTemplate(String userEmail, Long companyOwnerId, Long id, Long productId) {
        companyService.checkAccess(userEmail, companyOwnerId);
        
        LabelTemplate template = labelTemplateRepository.findByIdAndCompanyIdOrSystem(id, companyOwnerId);
        if (template == null) {
            throw new ResourceNotFoundException("Шаблон с id=" + id + " не найден");
        }
        
        // Парсинг конфигурации шаблона
        LabelConfigDto config;
        try {
            config = objectMapper.readValue(template.getConfig(), LabelConfigDto.class);
        } catch (JsonProcessingException e) {
            throw new ValidationException("Ошибка парсинга конфигурации шаблона: " + e.getMessage());
        }
        
        // Создание этикетки из шаблона
        CreateLabelDto createDto = CreateLabelDto.builder()
                .productId(productId)
                .name(template.getName() + " (копия)")
                .config(config)
                .build();
        
        LabelResponseDto createdLabel = labelService.createLabel(userEmail, companyOwnerId, createDto);
        log.info("Применён шаблон id={} к продукту {} пользователем {}, создана этикетка id={}", 
                template.getId(), productId, userEmail, createdLabel.getId());
        
        return mapToDto(template);
    }

    private LabelTemplateDto mapToDto(LabelTemplate template) {
        return LabelTemplateDto.builder()
                .id(template.getId())
                .name(template.getName())
                .isSystem(template.getIsSystem())
                .companyId(template.getCompanyId())
                .userId(template.getUserId())
                .width(template.getWidth())
                .height(template.getHeight())
                .unit(template.getUnit())
                .config(template.getConfig())
                .previewUrl(template.getPreviewUrl())
                .createdAt(template.getCreatedAt())
                .updatedAt(template.getUpdatedAt())
                .build();
    }
}
