package org.ozonLabel.user.controller;

import org.ozonLabel.common.dto.ApiResponse;
import org.ozonLabel.common.dto.labelsize.*;
import org.ozonLabel.user.service.LabelSizeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/label-sizes")
@RequiredArgsConstructor
public class LabelSizeController {

    private final LabelSizeService labelSizeService;

    /**
     * Получить все шаблоны размеров компании
     */
    @GetMapping
    public ResponseEntity<ApiResponse> getAll(@RequestParam Long companyOwnerId) {
        List<LabelSizeResponse> sizes = labelSizeService.getAllByCompany(companyOwnerId);
        return ResponseEntity.ok(ApiResponse.success(null, sizes));
    }

    /**
     * Создать новый шаблон размера
     */
    @PostMapping
    public ResponseEntity<ApiResponse> create(
            @RequestParam Long companyOwnerId,
            @Valid @RequestBody CreateLabelSizeRequest request
    ) {
        LabelSizeResponse created = labelSizeService.create(companyOwnerId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Шаблон размера успешно создан", created));
    }

    /**
     * Обновить шаблон размера
     */
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse> update(
            @PathVariable Long id,
            @RequestParam Long companyOwnerId,
            @Valid @RequestBody UpdateLabelSizeRequest request
    ) {
        LabelSizeResponse updated = labelSizeService.update(id, companyOwnerId, request);
        return ResponseEntity.ok(ApiResponse.success("Шаблон размера успешно обновлен", updated));
    }

    /**
     * Удалить шаблон размера
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse> delete(
            @PathVariable Long id,
            @RequestParam Long companyOwnerId
    ) {
        labelSizeService.delete(id, companyOwnerId);
        return ResponseEntity.ok(ApiResponse.success("Шаблон размера успешно удален"));
    }
}
