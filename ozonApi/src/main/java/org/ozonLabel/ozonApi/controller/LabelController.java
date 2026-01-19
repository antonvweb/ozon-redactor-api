package org.ozonLabel.ozonApi.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ozonLabel.common.dto.label.CreateLabelDto;
import org.ozonLabel.common.dto.label.LabelResponseDto;
import org.ozonLabel.common.dto.label.UpdateLabelDto;
import org.ozonLabel.common.service.label.LabelService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/labels")
@RequiredArgsConstructor
@Slf4j
public class LabelController {

    private final LabelService labelService;

    /**
     * Создать новую этикетку
     * POST /api/labels
     */
    @PostMapping
    public ResponseEntity<LabelResponseDto> createLabel(
            Authentication authentication,
            @Valid @RequestBody CreateLabelDto dto) {

        String userEmail = authentication.getName();
        log.info("Создание этикетки для товара {} пользователем {}", dto.getProductId(), userEmail);

        LabelResponseDto response = labelService.createLabel(userEmail, dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Обновить этикетку
     * PUT /api/labels/product/{productId}
     */
    @PutMapping("/product/{productId}")
    public ResponseEntity<LabelResponseDto> updateLabel(
            Authentication authentication,
            @PathVariable Long productId,
            @Valid @RequestBody UpdateLabelDto dto) {

        String userEmail = authentication.getName();
        log.info("Обновление этикетки для товара {} пользователем {}", productId, userEmail);

        LabelResponseDto response = labelService.updateLabel(userEmail, productId, dto);
        return ResponseEntity.ok(response);
    }

    /**
     * Получить этикетку для товара
     * GET /api/labels/product/{productId}
     */
    @GetMapping("/product/{productId}")
    public ResponseEntity<LabelResponseDto> getLabelByProductId(
            Authentication authentication,
            @PathVariable Long productId) {

        String userEmail = authentication.getName();
        log.info("Получение этикетки для товара {} пользователем {}", productId, userEmail);

        return labelService.getLabelByProductId(userEmail, productId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Получить все этикетки пользователя
     * GET /api/labels?page=0&size=20
     */
    @GetMapping
    public ResponseEntity<Page<LabelResponseDto>> getAllLabels(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        String userEmail = authentication.getName();
        log.info("Получение всех этикеток пользователя {} (page={}, size={})", userEmail, page, size);

        Pageable pageable = PageRequest.of(page, size);
        Page<LabelResponseDto> response = labelService.getAllLabels(userEmail, pageable);

        return ResponseEntity.ok(response);
    }

    /**
     * Получить все этикетки без пагинации
     * GET /api/labels/all
     */
    @GetMapping("/all")
    public ResponseEntity<List<LabelResponseDto>> getAllLabelsNoPagination(
            Authentication authentication) {

        String userEmail = authentication.getName();
        log.info("Получение всех этикеток пользователя {}", userEmail);

        List<LabelResponseDto> response = labelService.getAllLabels(userEmail);
        return ResponseEntity.ok(response);
    }

    /**
     * Удалить этикетку
     * DELETE /api/labels/product/{productId}
     */
    @DeleteMapping("/product/{productId}")
    public ResponseEntity<Map<String, String>> deleteLabel(
            Authentication authentication,
            @PathVariable Long productId) {

        String userEmail = authentication.getName();
        log.info("Удаление этикетки для товара {} пользователем {}", productId, userEmail);

        labelService.deleteLabel(userEmail, productId);

        return ResponseEntity.ok(Map.of(
                "success", "true",
                "message", "Этикетка успешно удалена"
        ));
    }

    /**
     * Проверить существование этикетки
     * GET /api/labels/product/{productId}/exists
     */
    @GetMapping("/product/{productId}/exists")
    public ResponseEntity<Map<String, Boolean>> checkLabelExists(
            Authentication authentication,
            @PathVariable Long productId) {

        String userEmail = authentication.getName();
        boolean exists = labelService.existsLabel(userEmail, productId);

        return ResponseEntity.ok(Map.of("exists", exists));
    }

    /**
     * Поиск этикеток по имени
     * GET /api/labels/search?name=test
     */
    @GetMapping("/search")
    public ResponseEntity<List<LabelResponseDto>> searchLabels(
            Authentication authentication,
            @RequestParam String name) {

        String userEmail = authentication.getName();
        log.info("Поиск этикеток по имени '{}' для пользователя {}", name, userEmail);

        List<LabelResponseDto> response = labelService.searchLabelsByName(userEmail, name);
        return ResponseEntity.ok(response);
    }
}