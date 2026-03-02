package org.ozonLabel.user.controller;

import org.ozonLabel.common.dto.ApiResponse;
import org.ozonLabel.common.dto.labelsize.*;
import org.ozonLabel.common.exception.user.AccessDeniedException;
import org.ozonLabel.common.model.MemberRole;
import org.ozonLabel.common.service.user.CompanyService;
import org.ozonLabel.user.service.LabelSizeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/label-sizes")
@RequiredArgsConstructor
public class LabelSizeController {

    private final LabelSizeService labelSizeService;
    private final CompanyService companyService;

    /**
     * Получить все шаблоны размеров компании
     */
    @GetMapping
    public ResponseEntity<ApiResponse> getAll(
            @RequestParam Long companyOwnerId,
            Authentication auth
    ) {
        String userEmail = auth.getName();
        
        // Проверка прав доступа к компании
        if (!companyService.hasMinimumRole(userEmail, companyOwnerId, MemberRole.VIEWER)) {
            throw new AccessDeniedException(
                    "Нет доступа к размерам компании " + companyOwnerId
            );
        }
        
        List<LabelSizeResponse> sizes = labelSizeService.getAllByCompany(companyOwnerId);
        return ResponseEntity.ok(ApiResponse.success(null, sizes));
    }

    /**
     * Создать новый шаблон размера
     */
    @PostMapping
    public ResponseEntity<ApiResponse> create(
            @RequestParam Long companyOwnerId,
            @Valid @RequestBody CreateLabelSizeRequest request,
            Authentication auth
    ) {
        String userEmail = auth.getName();
        
        // Проверка прав доступа к компании (нужна роль как минимум MODERATOR)
        if (!companyService.hasMinimumRole(userEmail, companyOwnerId, MemberRole.MODERATOR)) {
            throw new AccessDeniedException(
                    "Нет прав для создания размеров в компании " + companyOwnerId
            );
        }
        
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
            @Valid @RequestBody UpdateLabelSizeRequest request,
            Authentication auth
    ) {
        String userEmail = auth.getName();
        
        // Проверка прав доступа к компании (нужна роль как минимум MODERATOR)
        if (!companyService.hasMinimumRole(userEmail, companyOwnerId, MemberRole.MODERATOR)) {
            throw new AccessDeniedException(
                    "Нет прав для редактирования размеров в компании " + companyOwnerId
            );
        }
        
        LabelSizeResponse updated = labelSizeService.update(id, companyOwnerId, request);
        return ResponseEntity.ok(ApiResponse.success("Шаблон размера успешно обновлен", updated));
    }

    /**
     * Удалить шаблон размера
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse> delete(
            @PathVariable Long id,
            @RequestParam Long companyOwnerId,
            Authentication auth
    ) {
        String userEmail = auth.getName();
        
        // Проверка прав доступа к компании (нужна роль ADMIN для удаления)
        if (!companyService.hasMinimumRole(userEmail, companyOwnerId, MemberRole.ADMIN)) {
            throw new AccessDeniedException(
                    "Нет прав для удаления размеров в компании " + companyOwnerId
            );
        }
        
        labelSizeService.delete(id, companyOwnerId);
        return ResponseEntity.ok(ApiResponse.success("Шаблон размера успешно удален"));
    }
}
