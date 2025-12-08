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
import org.ozonLabel.ozonApi.exception.*;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    @Transactional
    @CacheEvict(value = "folderTrees", key = "#userEmail")
    public FolderResponseDto createFolder(String userEmail, CreateFolderDto dto) {
        User user = getUserByEmail(userEmail);

        if (dto.getParentFolderId() != null) {
            validateFolderOwnership(user.getId(), dto.getParentFolderId());
        }

        if (folderRepository.findByUserIdAndNameAndParentFolderId(
                user.getId(), dto.getName(), dto.getParentFolderId()).isPresent()) {
            throw new InvalidFolderOperationException("Folder with this name already exists");
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
        log.info("Created folder '{}' for user {}", folder.getName(), userEmail);

        return mapToDto(folder);
    }

    @Transactional
    @CacheEvict(value = "folderTrees", key = "#userEmail")
    public FolderResponseDto updateFolder(String userEmail, Long folderId, UpdateFolderDto dto) {
        User user = getUserByEmail(userEmail);
        ProductFolder folder = getFolderWithAccessCheck(folderId, user.getId());

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
    @CacheEvict(value = "folderTrees", key = "#userEmail")
    public void deleteFolder(String userEmail, Long folderId, boolean moveProductsToParent) {
        User user = getUserByEmail(userEmail);
        ProductFolder folder = getFolderWithAccessCheck(folderId, user.getId());

        if (moveProductsToParent) {
            moveItemsToParent(user.getId(), folderId, folder.getParentFolderId());
        } else {
            clearFolderAndSubfolders(user.getId(), folderId);
        }

        folderRepository.deleteById(folderId);
        log.info("Deleted folder {} for user {}", folderId, userEmail);
    }

    @Cacheable(value = "folderTrees", key = "#userEmail")
    public List<FolderTreeDto> getFolderTree(String userEmail) {
        User user = getUserByEmail(userEmail);

        List<ProductFolder> rootFolders = folderRepository
                .findByUserIdAndParentFolderIdIsNullOrderByPositionAsc(user.getId());

        return rootFolders.stream()
                .map(folder -> buildFolderTree(folder, user.getId()))
                .collect(Collectors.toList());
    }

    public List<FolderResponseDto> getFolders(String userEmail, Long parentFolderId) {
        User user = getUserByEmail(userEmail);

        List<ProductFolder> folders = parentFolderId == null
                ? folderRepository.findByUserIdAndParentFolderIdIsNullOrderByPositionAsc(user.getId())
                : folderRepository.findByUserIdAndParentFolderIdOrderByPositionAsc(user.getId(), parentFolderId);

        return folders.stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    public FolderResponseDto getFolder(String userEmail, Long folderId) {
        User user = getUserByEmail(userEmail);
        ProductFolder folder = getFolderWithAccessCheck(folderId, user.getId());
        return mapToDto(folder);
    }

    @Transactional
    public void moveProductsToFolder(String userEmail, MoveProductsToFolderDto dto) {
        User user = getUserByEmail(userEmail);

        if (dto.getTargetFolderId() != null) {
            validateFolderOwnership(user.getId(), dto.getTargetFolderId());
        }

        // Fetch all products in one query
        List<OzonProduct> products = productRepository.findAllById(dto.getProductIds());

        // Validate all products belong to user
        boolean allValid = products.stream()
                .allMatch(p -> p.getUserId().equals(user.getId()));

        if (!allValid) {
            throw new FolderAccessDeniedException("Some products don't belong to you");
        }

        // Use bulk update instead of saveAll
        int updated = productRepository.bulkMoveProductsToFolder(
                dto.getProductIds(),
                user.getId(),
                dto.getTargetFolderId()
        );

        log.info("Moved {} products to folder {} for user {}",
                updated, dto.getTargetFolderId(), userEmail);
    }

    public List<FolderPathDto> getFolderPath(String userEmail, Long folderId) {
        User user = getUserByEmail(userEmail);
        ProductFolder folder = getFolderWithAccessCheck(folderId, user.getId());

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
            throw new FolderAccessDeniedException("Access denied to this folder");
        }

        return folder;
    }

    private void validateFolderOwnership(Long userId, Long folderId) {
        if (!folderRepository.existsByUserIdAndId(userId, folderId)) {
            throw new FolderNotFoundException("Folder not found");
        }
    }

    private void validateFolderMove(Long folderId, Long targetParentId) {
        if (folderId.equals(targetParentId)) {
            throw new InvalidFolderOperationException("Cannot move folder into itself");
        }

        if (isSubfolderOf(targetParentId, folderId)) {
            throw new InvalidFolderOperationException("Cannot move folder into its subfolder");
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

    private User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
    }
}