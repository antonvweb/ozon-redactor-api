package org.ozonLabel.ozonApi.service;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ozonLabel.domain.model.OzonProduct;
import org.ozonLabel.domain.model.ProductFolder;
import org.ozonLabel.domain.model.User;
import org.ozonLabel.domain.repository.OzonProductRepository;
import org.ozonLabel.domain.repository.ProductFolderRepository;
import org.ozonLabel.domain.repository.UserRepository;
import org.ozonLabel.ozonApi.dto.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class FolderService {

    private final ProductFolderRepository folderRepository;
    private final OzonProductRepository productRepository;
    private final UserRepository userRepository;

    /**
     * Создать новую папку
     */
    @Transactional
    public FolderResponseDto createFolder(String userEmail, CreateFolderDto dto) {
        User user = getUserByEmail(userEmail);

        // Проверяем, что родительская папка существует и принадлежит пользователю
        if (dto.getParentFolderId() != null) {
            if (!folderRepository.existsByUserIdAndId(user.getId(), dto.getParentFolderId())) {
                throw new IllegalArgumentException("Родительская папка не найдена");
            }
        }

        // Проверяем уникальность имени в рамках родительской папки
        if (folderRepository.findByUserIdAndNameAndParentFolderId(
                user.getId(), dto.getName(), dto.getParentFolderId()).isPresent()) {
            throw new IllegalArgumentException("Папка с таким именем уже существует");
        }

        ProductFolder folder = ProductFolder.builder()
                .userId(user.getId())
                .parentFolderId(dto.getParentFolderId())
                .name(dto.getName())
                .color(dto.getColor())
                .icon(dto.getIcon())
                .position(0)
                .build();

        folder = folderRepository.save(folder);

        log.info("Создана папка {} для пользователя {}", folder.getName(), userEmail);

        return mapToDto(folder);
    }

    /**
     * Обновить папку
     */
    @Transactional
    public FolderResponseDto updateFolder(String userEmail, Long folderId, UpdateFolderDto dto) {
        User user = getUserByEmail(userEmail);

        ProductFolder folder = folderRepository.findById(folderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Папка не найдена"));

        if (!folder.getUserId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Нет доступа к этой папке");
        }

        // Проверяем, не пытается ли пользователь переместить папку в саму себя или в свою подпапку
        if (dto.getParentFolderId() != null) {
            if (dto.getParentFolderId().equals(folderId)) {
                throw new IllegalArgumentException("Нельзя переместить папку в саму себя");
            }

            // Проверяем, не является ли целевая папка подпапкой текущей
            if (isSubfolderOf(dto.getParentFolderId(), folderId)) {
                throw new IllegalArgumentException("Нельзя переместить папку в свою подпапку");
            }
        }

        if (dto.getName() != null) folder.setName(dto.getName());
        if (dto.getParentFolderId() != null) folder.setParentFolderId(dto.getParentFolderId());
        if (dto.getColor() != null) folder.setColor(dto.getColor());
        if (dto.getIcon() != null) folder.setIcon(dto.getIcon());
        if (dto.getPosition() != null) folder.setPosition(dto.getPosition());

        folder = folderRepository.save(folder);

        log.info("Обновлена папка {} для пользователя {}", folderId, userEmail);

        return mapToDto(folder);
    }

    /**
     * Удалить папку
     */
    @Transactional
    public void deleteFolder(String userEmail, Long folderId, boolean moveProductsToParent) {
        User user = getUserByEmail(userEmail);

        ProductFolder folder = folderRepository.findById(folderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Папка не найдена"));

        if (!folder.getUserId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Нет доступа к этой папке");
        }

        if (moveProductsToParent) {
            // Перемещаем товары в родительскую папку
            List<OzonProduct> products = productRepository.findByUserIdAndFolderId(user.getId(), folderId);
            products.forEach(product -> product.setFolderId(folder.getParentFolderId()));
            productRepository.saveAll(products);

            // Перемещаем подпапки
            List<ProductFolder> subfolders = folderRepository.findByUserIdAndParentFolderIdOrderByPositionAsc(
                    user.getId(), folderId);
            subfolders.forEach(subfolder -> subfolder.setParentFolderId(folder.getParentFolderId()));
            folderRepository.saveAll(subfolders);
        } else {
            // Удаляем все товары из папки и подпапок
            List<Long> allFolderIds = folderRepository.getAllSubfolderIds(folderId);
            allFolderIds.add(folderId);

            for (Long folId : allFolderIds) {
                List<OzonProduct> products = productRepository.findByUserIdAndFolderId(user.getId(), folId);
                products.forEach(product -> product.setFolderId(null));
                productRepository.saveAll(products);
            }
        }

        folderRepository.deleteById(folderId);

        log.info("Удалена папка {} для пользователя {}", folderId, userEmail);
    }

    /**
     * Получить дерево папок
     */
    public List<FolderTreeDto> getFolderTree(String userEmail) {
        User user = getUserByEmail(userEmail);

        List<ProductFolder> rootFolders = folderRepository
                .findByUserIdAndParentFolderIdIsNullOrderByPositionAsc(user.getId());

        return rootFolders.stream()
                .map(folder -> buildFolderTree(folder, user.getId()))
                .collect(Collectors.toList());
    }

    /**
     * Получить папки по уровню
     */
    public List<FolderResponseDto> getFolders(String userEmail, Long parentFolderId) {
        User user = getUserByEmail(userEmail);

        List<ProductFolder> folders;
        if (parentFolderId == null) {
            folders = folderRepository.findByUserIdAndParentFolderIdIsNullOrderByPositionAsc(user.getId());
        } else {
            folders = folderRepository.findByUserIdAndParentFolderIdOrderByPositionAsc(user.getId(), parentFolderId);
        }

        return folders.stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    /**
     * Получить информацию о папке
     */
    public FolderResponseDto getFolder(String userEmail, Long folderId) {
        User user = getUserByEmail(userEmail);

        ProductFolder folder = folderRepository.findById(folderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Папка не найдена"));

        if (!folder.getUserId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Нет доступа к этой папке");
        }

        return mapToDto(folder);
    }

    /**
     * Переместить товары в папку
     */
    @Transactional
    public void moveProductsToFolder(String userEmail, MoveProductsToFolderDto dto) {
        User user = getUserByEmail(userEmail);

        // Проверяем, что папка существует и принадлежит пользователю
        if (dto.getTargetFolderId() != null) {
            if (!folderRepository.existsByUserIdAndId(user.getId(), dto.getTargetFolderId())) {
                throw new IllegalArgumentException("Целевая папка не найдена");
            }
        }

        List<OzonProduct> products = productRepository.findAllById(dto.getProductIds());

        // Проверяем, что все товары принадлежат пользователю
        boolean allBelongToUser = products.stream()
                .allMatch(product -> product.getUserId().equals(user.getId()));

        if (!allBelongToUser) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Некоторые товары вам не принадлежат");
        }

        products.forEach(product -> product.setFolderId(dto.getTargetFolderId()));
        productRepository.saveAll(products);

        log.info("Перемещено {} товаров в папку {} для пользователя {}",
                products.size(), dto.getTargetFolderId(), userEmail);
    }

    /**
     * Получить путь к папке (breadcrumb)
     */
    public List<FolderPathDto> getFolderPath(String userEmail, Long folderId) {
        User user = getUserByEmail(userEmail);

        ProductFolder folder = folderRepository.findById(folderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Папка не найдена"));

        if (!folder.getUserId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Нет доступа к этой папке");
        }

        List<String> pathNames = folderRepository.getFolderPath(folderId);
        List<FolderPathDto> path = new ArrayList<>();

        // Строим путь от корня к текущей папке
        ProductFolder current = folder;
        int level = 0;

        while (current != null) {
            path.add(0, FolderPathDto.builder()
                    .id(current.getId())
                    .name(current.getName())
                    .level(level++)
                    .build());

            if (current.getParentFolderId() != null) {
                current = folderRepository.findById(current.getParentFolderId()).orElse(null);
            } else {
                current = null;
            }
        }

        return path;
    }

    private FolderTreeDto buildFolderTree(ProductFolder folder, Long userId) {
        List<ProductFolder> subfolders = folderRepository
                .findByUserIdAndParentFolderIdOrderByPositionAsc(userId, folder.getId());

        Long productsCount = productRepository.countByUserIdAndFolderId(userId, folder.getId());

        return FolderTreeDto.builder()
                .id(folder.getId())
                .name(folder.getName())
                .color(folder.getColor())
                .icon(folder.getIcon())
                .position(folder.getPosition())
                .productsCount(productsCount.intValue())
                .subfolders(subfolders.stream()
                        .map(sf -> buildFolderTree(sf, userId))
                        .collect(Collectors.toList()))
                .build();
    }

    private FolderResponseDto mapToDto(ProductFolder folder) {
        Long productsCount = productRepository.countByUserIdAndFolderId(folder.getUserId(), folder.getId());
        Long subfoldersCount = folderRepository.countByUserIdAndParentFolderId(
                folder.getUserId(), folder.getId());

        return FolderResponseDto.builder()
                .id(folder.getId())
                .userId(folder.getUserId())
                .parentFolderId(folder.getParentFolderId())
                .name(folder.getName())
                .color(folder.getColor())
                .icon(folder.getIcon())
                .position(folder.getPosition())
                .productsCount(productsCount.intValue())
                .subfoldersCount(subfoldersCount.intValue())
                .createdAt(folder.getCreatedAt())
                .updatedAt(folder.getUpdatedAt())
                .build();
    }

    private boolean isSubfolderOf(Long potentialSubfolderId, Long parentId) {
        ProductFolder folder = folderRepository.findById(potentialSubfolderId).orElse(null);

        while (folder != null && folder.getParentFolderId() != null) {
            if (folder.getParentFolderId().equals(parentId)) {
                return true;
            }
            folder = folderRepository.findById(folder.getParentFolderId()).orElse(null);
        }

        return false;
    }

    private User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Пользователь не найден"));
    }
}