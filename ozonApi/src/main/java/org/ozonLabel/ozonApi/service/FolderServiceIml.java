package org.ozonLabel.ozonApi.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ozonLabel.common.dto.ozon.*;
import org.ozonLabel.common.exception.ozon.FolderAccessDeniedException;
import org.ozonLabel.common.exception.ozon.FolderNotFoundException;
import org.ozonLabel.common.exception.ozon.InvalidFolderOperationException;
import org.ozonLabel.common.service.ozon.FolderService;
import org.ozonLabel.common.service.user.CompanyService;
import org.ozonLabel.ozonApi.entity.OzonProduct;
import org.ozonLabel.ozonApi.entity.ProductFolder;
import org.ozonLabel.ozonApi.repository.OzonProductRepository;
import org.ozonLabel.ozonApi.repository.ProductFolderRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class FolderServiceIml implements FolderService {

    private final ProductFolderRepository folderRepository;
    private final OzonProductRepository productRepository;
    private final CompanyService companyService;

    @Transactional
    @CacheEvict(value = "folderTrees", key = "#companyOwnerId")
    public FolderResponseDto createFolder(String userEmail, Long companyOwnerId, CreateFolderDto dto) {
        companyService.checkAccess(userEmail, companyOwnerId);

        if (dto.getParentFolderId() != null) {
            validateFolderOwnership(companyOwnerId, dto.getParentFolderId());
        }

        if (folderRepository.findByUserIdAndNameAndParentFolderId(
                companyOwnerId, dto.getName(), dto.getParentFolderId()).isPresent()) {
            throw new InvalidFolderOperationException("Папка с таким именем уже существует.");
        }

        ProductFolder folder = ProductFolder.builder()
                .userId(companyOwnerId)
                .parentFolderId(dto.getParentFolderId())
                .name(dto.getName())
                .color(dto.getColor())
                .icon(dto.getIcon())
                .position(0)
                .build();

        folder = folderRepository.save(folder);
        log.info("Created folder '{}' for user {}", folder.getName(), userEmail);

        return mapToDto(folder);
    }

    @Transactional
    @CacheEvict(value = "folderTrees", key = "#companyOwnerId")
    public FolderResponseDto updateFolder(String userEmail, Long companyOwnerId, Long folderId, UpdateFolderDto dto) {
        // Проверяем доступ к компании (минимум MODERATOR для редактирования)
        companyService.checkAccess(userEmail, companyOwnerId);

        ProductFolder folder = getFolderWithAccessCheck(folderId, companyOwnerId);

        if (dto.getParentFolderId() != null) {
            validateFolderMove(folderId, dto.getParentFolderId());
        }

        if (dto.getName() != null) folder.setName(dto.getName());
        if (dto.getParentFolderId() != null) folder.setParentFolderId(dto.getParentFolderId());
        if (dto.getColor() != null) folder.setColor(dto.getColor());
        if (dto.getIcon() != null) folder.setIcon(dto.getIcon());
        if (dto.getPosition() != null) folder.setPosition(dto.getPosition());

        folder = folderRepository.save(folder);
        log.info("Updated folder {} for user {}", folderId, userEmail);

        return mapToDto(folder);
    }

    @Transactional
    @CacheEvict(value = "folderTrees", key = "#companyOwnerId")
    public void deleteFolder(String userEmail, Long companyOwnerId, Long folderId, boolean moveProductsToParent) {
        // Проверяем доступ к компании (минимум MODERATOR для удаления)
        companyService.checkAccess(userEmail, companyOwnerId);

        ProductFolder folder = getFolderWithAccessCheck(folderId, companyOwnerId);

        if (moveProductsToParent) {
            moveItemsToParent(companyOwnerId, folderId, folder.getParentFolderId());
        } else {
            clearFolderAndSubfolders(companyOwnerId, folderId);
        }

        folderRepository.deleteById(folderId);
        log.info("Deleted folder {} for user {}", folderId, userEmail);
    }

    @Cacheable(value = "folderTrees", key = "#companyOwnerId")
    public List<FolderTreeDto> getFolderTree(String userEmail, Long companyOwnerId) {
        log.info("getFolderTree called: userEmail={}, companyOwnerId={}", userEmail, companyOwnerId);
        companyService.checkAccess(userEmail, companyOwnerId);

        List<ProductFolder> rootFolders = folderRepository
                .findByUserIdAndParentFolderIdIsNullOrderByPositionAsc(companyOwnerId);

        return rootFolders.stream()
                .map(folder -> buildFolderTree(folder, companyOwnerId))
                .collect(Collectors.toList());
    }

    public List<FolderResponseDto> getFolders(String userEmail, Long companyOwnerId, Long parentFolderId) {
        // Проверяем доступ к компании
        companyService.checkAccess(userEmail, companyOwnerId);

        List<ProductFolder> folders = parentFolderId == null
                ? folderRepository.findByUserIdAndParentFolderIdIsNullOrderByPositionAsc(companyOwnerId)
                : folderRepository.findByUserIdAndParentFolderIdOrderByPositionAsc(companyOwnerId, parentFolderId);

        return folders.stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    public FolderResponseDto getFolder(String userEmail, Long companyOwnerId, Long folderId) {
        // Проверяем доступ к компании
        companyService.checkAccess(userEmail, companyOwnerId);

        ProductFolder folder = getFolderWithAccessCheck(folderId, companyOwnerId);
        return mapToDto(folder);
    }

    @Transactional
    public void moveProductsToFolder(String userEmail, Long companyOwnerId, MoveProductsToFolderDto dto) {
        // Проверяем доступ к компании (минимум MODERATOR для перемещения)
        companyService.checkAccess(userEmail, companyOwnerId);

        if (dto.getTargetFolderId() != null) {
            validateFolderOwnership(companyOwnerId, dto.getTargetFolderId());
        }

        // Fetch all products in one query
        List<OzonProduct> products = productRepository.findAllById(dto.getProductIds());

        // Validate all products belong to company
        boolean allValid = products.stream()
                .allMatch(p -> p.getUserId().equals(companyOwnerId));

        if (!allValid) {
            throw new FolderAccessDeniedException("Некоторые товары не принадлежат этой компании.");
        }

        // Use bulk update instead of saveAll
        int updated = productRepository.bulkMoveProductsToFolder(
                dto.getProductIds(),
                companyOwnerId,
                dto.getTargetFolderId()
        );

        log.info("Moved {} products to folder {} for user {}",
                updated, dto.getTargetFolderId(), userEmail);
    }

    public List<FolderPathDto> getFolderPath(String userEmail, Long companyOwnerId, Long folderId) {
        // Проверяем доступ к компании
        companyService.checkAccess(userEmail, companyOwnerId);

        ProductFolder folder = getFolderWithAccessCheck(folderId, companyOwnerId);

        List<FolderPathDto> path = new ArrayList<>();
        ProductFolder current = folder;
        int level = 0;

        while (current != null) {
            path.add(0, FolderPathDto.builder()
                    .id(current.getId())
                    .name(current.getName())
                    .level(level++)
                    .build());

            current = current.getParentFolderId() != null
                    ? folderRepository.findById(current.getParentFolderId()).orElse(null)
                    : null;
        }

        return path;
    }

    @Override
    public List<FolderResponseDto> findByUserIdAndParentFolderIdIsNullOrderByPositionAsc(Long userId) {
        return folderRepository.findByUserIdAndParentFolderIdIsNullOrderByPositionAsc(userId)
                .stream()
                .map(this::mapToDto)
                .toList();
    }

    @Override
    public List<FolderResponseDto> findByUserIdAndParentFolderIdOrderByPositionAsc(Long userId, Long parentFolderId) {
        return folderRepository.findByUserIdAndParentFolderIdOrderByPositionAsc(userId, parentFolderId)
                .stream()
                .map(this::mapToDto)
                .toList();
    }

    @Override
    public List<FolderResponseDto> findByUserIdOrderByPositionAsc(Long userId) {
        return folderRepository.findByUserIdOrderByPositionAsc(userId)
                .stream()
                .map(this::mapToDto)
                .toList();
    }

    @Override
    public Optional<FolderResponseDto> findByUserIdAndNameAndParentFolderId(Long userId, String name, Long parentFolderId) {
        return folderRepository.findByUserIdAndNameAndParentFolderId(userId, name, parentFolderId)
                .map(this::mapToDto);
    }

    @Override
    public boolean existsByUserIdAndId(Long userId, Long folderId) {
        return folderRepository.existsByUserIdAndId(userId, folderId);
    }

    @Override
    public List<Object[]> getFolderTreeForUser(Long userId) {
        return folderRepository.getFolderTreeForUser(userId);
    }

    @Override
    public List<String> getFolderPath(Long folderId) {
        return folderRepository.getFolderPath(folderId);
    }

    @Override
    public Long countByUserIdAndParentFolderId(Long userId, Long parentFolderId) {
        return folderRepository.countByUserIdAndParentFolderId(userId, parentFolderId);
    }

    @Override
    public List<Long> getAllSubfolderIds(Long folderId) {
        return folderRepository.getAllSubfolderIds(folderId);
    }


    // Private helper methods

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
        Long productsCount = productRepository.countByUserIdAndFolderId(
                folder.getUserId(), folder.getId());
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

    private ProductFolder getFolderWithAccessCheck(Long folderId, Long userId) {
        ProductFolder folder = folderRepository.findById(folderId)
                .orElseThrow(() -> new FolderNotFoundException("Folder not found"));

        if (!folder.getUserId().equals(userId)) {
            throw new FolderAccessDeniedException("Доступ к этой папке запрещен.");
        }

        return folder;
    }

    private void validateFolderOwnership(Long userId, Long folderId) {
        if (!folderRepository.existsByUserIdAndId(userId, folderId)) {
            throw new FolderNotFoundException("Папка не найдена");
        }
    }

    private void validateFolderMove(Long folderId, Long targetParentId) {
        if (folderId.equals(targetParentId)) {
            throw new InvalidFolderOperationException("Не удаётся переместить папку саму в себя.");
        }

        if (isSubfolderOf(targetParentId, folderId)) {
            throw new InvalidFolderOperationException("Не удаётся переместить папку в её подпапку.");
        }
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

    private void moveItemsToParent(Long userId, Long folderId, Long parentFolderId) {
        // Move products
        List<OzonProduct> products = productRepository.findByUserIdAndFolderId(userId, folderId);
        products.forEach(p -> p.setFolderId(parentFolderId));
        productRepository.saveAll(products);

        // Move subfolders
        List<ProductFolder> subfolders = folderRepository
                .findByUserIdAndParentFolderIdOrderByPositionAsc(userId, folderId);
        subfolders.forEach(sf -> sf.setParentFolderId(parentFolderId));
        folderRepository.saveAll(subfolders);
    }

    private void clearFolderAndSubfolders(Long userId, Long folderId) {
        List<Long> allFolderIds = folderRepository.getAllSubfolderIds(folderId);
        allFolderIds.add(folderId);

        for (Long folId : allFolderIds) {
            List<OzonProduct> products = productRepository.findByUserIdAndFolderId(userId, folId);
            products.forEach(p -> p.setFolderId(null));
            productRepository.saveAll(products);
        }
    }
}