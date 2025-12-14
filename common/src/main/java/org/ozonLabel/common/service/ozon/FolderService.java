package org.ozonLabel.common.service.ozon;

import org.ozonLabel.common.dto.ozon.*;

import java.util.List;
import java.util.Optional;

public interface FolderService {

    // Создание новой папки
    FolderResponseDto createFolder(String userEmail, Long companyOwnerId, CreateFolderDto dto);

    // Обновление папки
    FolderResponseDto updateFolder(String userEmail, Long companyOwnerId, Long folderId, UpdateFolderDto dto);

    // Удаление папки с опцией перемещения товаров в родительскую
    void deleteFolder(String userEmail, Long companyOwnerId, Long folderId, boolean moveProductsToParent);

    // Получение дерева папок
    List<FolderTreeDto> getFolderTree(String userEmail, Long companyOwnerId);

    // Получение списка папок по родительской
    List<FolderResponseDto> getFolders(String userEmail, Long companyOwnerId, Long parentFolderId);

    // Получение конкретной папки
    FolderResponseDto getFolder(String userEmail, Long companyOwnerId, Long folderId);

    // Перемещение товаров в папку
    void moveProductsToFolder(String userEmail, Long companyOwnerId, MoveProductsToFolderDto dto);

    // Получение пути к папке
    List<FolderPathDto> getFolderPath(String userEmail, Long companyOwnerId, Long folderId);

    List<FolderResponseDto> findByUserIdAndParentFolderIdIsNullOrderByPositionAsc(Long userId);
    List<FolderResponseDto> findByUserIdAndParentFolderIdOrderByPositionAsc(Long userId, Long parentFolderId);
    List<FolderResponseDto> findByUserIdOrderByPositionAsc(Long userId);
    Optional<FolderResponseDto> findByUserIdAndNameAndParentFolderId(Long userId, String name, Long parentFolderId);
    boolean existsByUserIdAndId(Long userId, Long folderId);
    List<Object[]> getFolderTreeForUser(Long userId);
    List<String> getFolderPath(Long folderId);
    Long countByUserIdAndParentFolderId(Long userId, Long parentFolderId);
    List<Long> getAllSubfolderIds(Long folderId);
}
