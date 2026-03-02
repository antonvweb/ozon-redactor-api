package org.ozonLabel.common.service.template;

import org.ozonLabel.common.dto.template.CreateLabelTemplateDto;
import org.ozonLabel.common.dto.template.LabelTemplateDto;
import org.ozonLabel.common.dto.template.UpdateLabelTemplateDto;

import java.util.List;

/**
 * Сервис для управления шаблонами этикеток
 */
public interface LabelTemplateService {
    
    /**
     * Получить все шаблоны (системные + пользовательские)
     */
    List<LabelTemplateDto> getAllTemplates(String userEmail, Long companyOwnerId);
    
    /**
     * Создать пользовательский шаблон
     */
    LabelTemplateDto createTemplate(String userEmail, Long companyOwnerId, CreateLabelTemplateDto dto);
    
    /**
     * Обновить шаблон
     */
    LabelTemplateDto updateTemplate(String userEmail, Long companyOwnerId, Long id, UpdateLabelTemplateDto dto);
    
    /**
     * Удалить шаблон (только не системные)
     */
    void deleteTemplate(String userEmail, Long companyOwnerId, Long id);
    
    /**
     * Применить шаблон к продукту (создаёт Label из шаблона)
     */
    LabelTemplateDto applyTemplate(String userEmail, Long companyOwnerId, Long id, Long productId);
}
