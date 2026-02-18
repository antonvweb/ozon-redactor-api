package org.ozonLabel.ozonApi.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ozonLabel.common.dto.ApiResponse;
import org.ozonLabel.common.dto.ozon.*;
import org.ozonLabel.common.service.ozon.FolderService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;

import java.util.List;

@RestController
@RequestMapping("/api/folders")
@RequiredArgsConstructor
@Slf4j
public class FolderController {

    private final FolderService folderService;

    /**
     * Создать новую папку
     */
    @PostMapping
    public ResponseEntity<FolderResponseDto> createFolder(
            @RequestParam Long companyOwnerId,
            @RequestBody CreateFolderDto dto,
            Authentication auth) {

        String userEmail = auth.getName();
        log.info("Создание папки '{}' для компании {} пользователем {}", dto.getName(), companyOwnerId, userEmail);

        FolderResponseDto response = folderService.createFolder(userEmail, companyOwnerId, dto);
        return ResponseEntity.ok(response);
    }

    /**
     * Обновить папку
     */
    @PutMapping("/{folderId}")
    public ResponseEntity<FolderResponseDto> updateFolder(
            @PathVariable Long folderId,
            @RequestParam Long companyOwnerId,
            @RequestBody UpdateFolderDto dto,
            Authentication auth) {

        String userEmail = auth.getName();
        log.info("Обновление папки {} компании {} пользователем {}", folderId, companyOwnerId, userEmail);

        FolderResponseDto response = folderService.updateFolder(userEmail, companyOwnerId, folderId, dto);
        return ResponseEntity.ok(response);
    }

    /**
     * Удалить папку
     */
    @DeleteMapping("/{folderId}")
    public ResponseEntity<ApiResponse> deleteFolder(
            @PathVariable Long folderId,
            @RequestParam Long companyOwnerId,
            @RequestParam(defaultValue = "false") boolean moveProductsToParent,
            Authentication auth) {

        String userEmail = auth.getName();
        log.info("Удаление папки {} компании {} пользователем {} (moveToParent: {})",
                folderId, companyOwnerId, userEmail, moveProductsToParent);

        folderService.deleteFolder(userEmail, companyOwnerId, folderId, moveProductsToParent);
        return ResponseEntity.ok(ApiResponse.success("Папка успешно удалена"));
    }

    /**
     * Получить папки определенного уровн
     */
    @GetMapping("/tree")
    public ResponseEntity<List<FolderTreeDto>> getFolderTree(
            @RequestParam Long companyOwnerId,
            Authentication auth) {
        String userEmail = auth.getName();
        log.info("Получение дерева папок компании {} пользователем {}", companyOwnerId, userEmail);

        List<FolderTreeDto> tree = folderService.getFolderTree(userEmail, companyOwnerId);
        return ResponseEntity.ok(tree);
    }

    /**
     * Получить папки определенного уровня
     */
    @GetMapping
    public ResponseEntity<List<FolderResponseDto>> getFolders(
            @RequestParam Long companyOwnerId,
            @RequestParam(required = false) Long parentFolderId,
            Authentication auth) {

        String userEmail = auth.getName();
        log.info("Получение папок уровня {} компании {} пользователем {}", parentFolderId, companyOwnerId, userEmail);

        List<FolderResponseDto> folders = folderService.getFolders(userEmail, companyOwnerId, parentFolderId);
        return ResponseEntity.ok(folders);
    }

    /**
     * Получить информацию о папке
     */
    @GetMapping("/{folderId}")
    public ResponseEntity<FolderResponseDto> getFolder(
            @PathVariable Long folderId,
            @RequestParam Long companyOwnerId,
            Authentication auth) {

        String userEmail = auth.getName();
        FolderResponseDto response = folderService.getFolder(userEmail, companyOwnerId, folderId);
        return ResponseEntity.ok(response);
    }

    /**
     * Переместить товары в папку
     */
    @PostMapping("/move-products")
    public ResponseEntity<ApiResponse> moveProductsToFolder(
            @RequestParam Long companyOwnerId,
            @RequestBody MoveProductsToFolderDto dto,
            Authentication auth) {

        String userEmail = auth.getName();
        log.info("Перемещение {} товаров в папку {} компании {} пользователем {}",
                dto.getProductIds().size(), dto.getTargetFolderId(), companyOwnerId, userEmail);

        folderService.moveProductsToFolder(userEmail, companyOwnerId, dto);
        return ResponseEntity.ok(ApiResponse.success("Товары успешно перемещены"));
    }

    /**
     * Получить путь к папке (breadcrumb)
     */
    @GetMapping("/{folderId}/path")
    public ResponseEntity<List<FolderPathDto>> getFolderPath(
            @PathVariable Long folderId,
            @RequestParam Long companyOwnerId,
            Authentication auth) {

        String userEmail = auth.getName();
        List<FolderPathDto> path = folderService.getFolderPath(userEmail, companyOwnerId, folderId);
        return ResponseEntity.ok(path);
    }
}
