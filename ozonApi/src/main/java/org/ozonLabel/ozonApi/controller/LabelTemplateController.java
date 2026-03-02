package org.ozonLabel.ozonApi.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ozonLabel.common.dto.ApiResponse;
import org.ozonLabel.common.dto.template.CreateLabelTemplateDto;
import org.ozonLabel.common.dto.template.LabelTemplateDto;
import org.ozonLabel.common.dto.template.UpdateLabelTemplateDto;
import org.ozonLabel.common.service.template.LabelTemplateService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/templates")
@RequiredArgsConstructor
@Slf4j
public class LabelTemplateController {

    private final LabelTemplateService labelTemplateService;

    /**
     * Получить все шаблоны (системные + пользовательские)
     */
    @GetMapping
    public ResponseEntity<List<LabelTemplateDto>> getAllTemplates(
            @RequestParam Long companyOwnerId,
            Authentication auth) {

        String userEmail = auth.getName();
        log.info("Получение всех шаблонов компании {} пользователем {}", companyOwnerId, userEmail);

        List<LabelTemplateDto> templates = labelTemplateService.getAllTemplates(userEmail, companyOwnerId);
        return ResponseEntity.ok(templates);
    }

    /**
     * Создать пользовательский шаблон
     */
    @PostMapping
    public ResponseEntity<LabelTemplateDto> createTemplate(
            @RequestParam Long companyOwnerId,
            @Valid @RequestBody CreateLabelTemplateDto dto,
            Authentication auth) {

        String userEmail = auth.getName();
        log.info("Создание шаблона для компании {} пользователем {}", companyOwnerId, userEmail);

        LabelTemplateDto response = labelTemplateService.createTemplate(userEmail, companyOwnerId, dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Обновить шаблон
     */
    @PutMapping("/{id}")
    public ResponseEntity<LabelTemplateDto> updateTemplate(
            @PathVariable Long id,
            @RequestParam Long companyOwnerId,
            @Valid @RequestBody UpdateLabelTemplateDto dto,
            Authentication auth) {

        String userEmail = auth.getName();
        log.info("Обновление шаблона {} компании {} пользователем {}", id, companyOwnerId, userEmail);

        LabelTemplateDto response = labelTemplateService.updateTemplate(userEmail, companyOwnerId, id, dto);
        return ResponseEntity.ok(response);
    }

    /**
     * Удалить шаблон (только не системные)
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse> deleteTemplate(
            @PathVariable Long id,
            @RequestParam Long companyOwnerId,
            Authentication auth) {

        String userEmail = auth.getName();
        log.info("Удаление шаблона {} компании {} пользователем {}", id, companyOwnerId, userEmail);

        labelTemplateService.deleteTemplate(userEmail, companyOwnerId, id);
        return ResponseEntity.ok(ApiResponse.success("Шаблон успешно удалён"));
    }

    /**
     * Применить шаблон к продукту (создаёт Label из шаблона)
     */
    @PostMapping("/{id}/apply")
    public ResponseEntity<LabelTemplateDto> applyTemplate(
            @PathVariable Long id,
            @RequestParam Long companyOwnerId,
            @RequestParam Long productId,
            Authentication auth) {

        String userEmail = auth.getName();
        log.info("Применение шаблона {} к продукту {} компании {} пользователем {}", 
                id, productId, companyOwnerId, userEmail);

        LabelTemplateDto response = labelTemplateService.applyTemplate(userEmail, companyOwnerId, id, productId);
        return ResponseEntity.ok(response);
    }
}
