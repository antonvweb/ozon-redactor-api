package org.ozonLabel.ozonApi.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ozonLabel.common.dto.ApiResponse;
import org.ozonLabel.ozonApi.dto.*;
import org.ozonLabel.ozonApi.service.FolderService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;

import java.util.List;

@RestController
@RequestMapping("/api/folders")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Slf4j
public class FolderController {

    private final FolderService folderService;

    /**
     * Создать новую папку
     */
    @PostMapping
    public ResponseEntity<FolderResponseDto> createFolder(
            @RequestBody CreateFolderDto dto,
            Authentication auth) {

        String userEmail = auth.getName();
        log.info("Создание папки '{}' для пользовател {}", dto.getName(), userEmail);

        FolderResponseDto response = folderService.createFolder(userEmail, dto);
        return ResponseEntity.ok(response);
    }

    /**
     * Обновить папку
     */
    @PutMapping("/{folderId}")
    public ResponseEntity<FolderResponseDto> updateFolder(
            @PathVariable Long folderId,
            @RequestBody UpdateFolderDto dto,
            Authentication auth) {

        String userEmail = auth.getName();
        log.info("Обновление папки {} для пользователя {}", folderId, userEmail);

        FolderResponseDto response = folderService.updateFolder(userEmail, folderId, dto);
        return ResponseEntity.ok(response);
    }

    /**
     * Удалить папку
     */
    @DeleteMapping("/{folderId}")
    public ResponseEntity<ApiResponse> deleteFolder(
            @PathVariable Long folderId,
            @RequestParam(defaultValue = "false") boolean moveProductsToParent,
            Authentication auth) {

        String userEmail = auth.getName();
        log.info("Удаление папки {} для пользователя {} (moveToParent: {})",
                folderId, userEmail, moveProductsToParent);

        folderService.deleteFolder(userEmail, folderId, moveProductsToParent);
        return ResponseEntity.ok(ApiResponse.success("Папка успешно удалена"));
    }

    /**
     * Получить дерево папок
     */
    @GetMapping("/tree")
    public ResponseEntity<List<FolderTreeDto>> getFolderTree(Authentication auth) {
        String userEmail = auth.getName();
        log.info("Получение дерева папок для пользователя {}", userEmail);

        List<FolderTreeDto> tree = folderService.getFolderTree(userEmail);
        return ResponseEntity.ok(tree);
    }

    /**
     * Получить папки определенного уровня
     */
    @GetMapping
    public ResponseEntity<List<FolderResponseDto>> getFolders(
            @RequestParam(required = false) Long parentFolderId,
            Authentication auth) {

        String userEmail = auth.getName();
        log.info("Получение папок уровня {} для пользователя {}", parentFolderId, userEmail);

        List<FolderResponseDto> folders = folderService.getFolders(userEmail, parentFolderId);
        return ResponseEntity.ok(folders);
    }

    /**
     * Получить информацию о папке
     */
    @GetMapping("/{folderId}")
    public ResponseEntity<FolderResponseDto> getFolder(
            @PathVariable Long folderId,
            Authentication auth) {

        String userEmail = auth.getName();
        FolderResponseDto response = folderService.getFolder(userEmail, folderId);
        return ResponseEntity.ok(response);
    }

    /**
     * Переместить товары в папку
     */
    @PostMapping("/move-products")
    public ResponseEntity<ApiResponse> moveProductsToFolder(
            @RequestBody MoveProductsToFolderDto dto,
            Authentication auth) {

        String userEmail = auth.getName();
        log.info("Перемещение {} товаров в папку {} для пользователя {}",
                dto.getProductIds().size(), dto.getTargetFolderId(), userEmail);

        folderService.moveProductsToFolder(userEmail, dto);
        return ResponseEntity.ok(ApiResponse.success("Товары успешно перемещены"));
    }

    /**
     * Получить путь к папке (breadcrumb)
     */
    @GetMapping("/{folderId}/path")
    public ResponseEntity<List<FolderPathDto>> getFolderPath(
            @PathVariable Long folderId,
            Authentication auth) {

        String userEmail = auth.getName();
        List<FolderPathDto> path = folderService.getFolderPath(userEmail, folderId);
        return ResponseEntity.ok(path);
    }
}
