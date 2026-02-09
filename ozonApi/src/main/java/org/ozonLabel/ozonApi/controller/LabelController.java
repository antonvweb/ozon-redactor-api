package org.ozonLabel.ozonApi.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ozonLabel.common.dto.ApiResponse;
import org.ozonLabel.common.dto.label.CreateLabelDto;
import org.ozonLabel.common.dto.label.LabelResponseDto;
import org.ozonLabel.common.dto.label.UpdateLabelDto;
import org.ozonLabel.common.service.label.LabelService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/labels")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Slf4j
public class LabelController {

    private final LabelService labelService;

    @PostMapping
    public ResponseEntity<LabelResponseDto> createLabel(
            @RequestParam Long companyOwnerId,
            @Valid @RequestBody CreateLabelDto dto,
            Authentication auth) {

        String userEmail = auth.getName();
        log.info("Создание этикетки для продукта {} компании {} пользователем {}",
                dto.getProductId(), companyOwnerId, userEmail);

        LabelResponseDto response = labelService.createLabel(userEmail, companyOwnerId, dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<LabelResponseDto> getLabel(
            @PathVariable Long id,
            @RequestParam Long companyOwnerId,
            Authentication auth) {

        String userEmail = auth.getName();
        log.info("Получение этикетки {} компании {} пользователем {}", id, companyOwnerId, userEmail);

        LabelResponseDto response = labelService.getLabel(userEmail, companyOwnerId, id);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/product/{productId}")
    public ResponseEntity<LabelResponseDto> getLabelByProductId(
            @PathVariable Long productId,
            @RequestParam Long companyOwnerId,
            Authentication auth) {

        String userEmail = auth.getName();
        log.info("Получение этикетки для продукта {} компании {} пользователем {}",
                productId, companyOwnerId, userEmail);

        LabelResponseDto response = labelService.getLabelByProductId(userEmail, companyOwnerId, productId);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<LabelResponseDto> updateLabel(
            @PathVariable Long id,
            @RequestParam Long companyOwnerId,
            @Valid @RequestBody UpdateLabelDto dto,
            Authentication auth) {

        String userEmail = auth.getName();
        log.info("Обновление этикетки {} компании {} пользователем {}", id, companyOwnerId, userEmail);

        LabelResponseDto response = labelService.updateLabel(userEmail, companyOwnerId, id, dto);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse> deleteLabel(
            @PathVariable Long id,
            @RequestParam Long companyOwnerId,
            Authentication auth) {

        String userEmail = auth.getName();
        log.info("Удаление этикетки {} компании {} пользователем {}", id, companyOwnerId, userEmail);

        labelService.deleteLabel(userEmail, companyOwnerId, id);
        return ResponseEntity.ok(ApiResponse.success("Этикетка успешно удалена"));
    }

    @PostMapping("/{id}/duplicate")
    public ResponseEntity<LabelResponseDto> duplicateLabel(
            @PathVariable Long id,
            @RequestParam Long companyOwnerId,
            @RequestParam Long targetProductId,
            Authentication auth) {

        String userEmail = auth.getName();
        log.info("Дублирование этикетки {} на продукт {} компании {} пользователем {}",
                id, targetProductId, companyOwnerId, userEmail);

        LabelResponseDto response = labelService.duplicateLabel(userEmail, companyOwnerId, id, targetProductId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/batch")
    public ResponseEntity<List<LabelResponseDto>> getLabelsByProductIds(
            @RequestParam Long companyOwnerId,
            @RequestBody List<Long> productIds,
            Authentication auth) {

        String userEmail = auth.getName();
        log.info("Batch получение этикеток для {} продуктов компании {} пользователем {}",
                productIds.size(), companyOwnerId, userEmail);

        List<LabelResponseDto> response = labelService.getLabelsByProductIds(userEmail, companyOwnerId, productIds);
        return ResponseEntity.ok(response);
    }
}
