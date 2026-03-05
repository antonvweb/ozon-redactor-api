package org.ozonLabel.common.service.ozon;

import org.ozonLabel.common.dto.ozon.*;
import org.ozonLabel.common.dto.label.LayerVisibilityRequest;

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

    // Обновление видимости слоя для всех этикеток в папке
    void updateLayerVisibility(String userEmail, Long companyOwnerId, Long folderId, LayerVisibilityRequest dto);

    // Обновление данных папки (ресинхронизация)
    void refreshFolder(String userEmail, Long companyOwnerId, Long folderId);

    // Переключение шаблона папки
    FolderResponseDto toggleTemplate(String userEmail, Long companyOwnerId, Long folderId, Boolean isTemplate);

    // Получение статистики DataMatrix по папке
    FolderDataMatrixStats getFolderDataMatrixStats(String userEmail, Long companyOwnerId, Long folderId);

    // Загрузка заказа для печати из Excel
    OrderUploadResult uploadPrintOrder(String userEmail, Long companyOwnerId, org.springframework.web.multipart.MultipartFile file);

    // Разрешение неоднозначностей при загрузке заказа
    void resolvePrintOrder(String userEmail, Long companyOwnerId, ResolveOrderRequest request);

    // Получение папок по типу источника
    List<FolderResponseDto> getFoldersBySourceType(String userEmail, Long companyOwnerId, String sourceType);

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
