package org.ozonLabel.ozonApi.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ozonLabel.common.dto.label.ImageUploadResponseDto;
import org.ozonLabel.common.dto.label.UserImageDto;
import org.ozonLabel.common.dto.label.UserImageListResponseDto;
import org.ozonLabel.common.dto.user.UserResponseDto;
import org.ozonLabel.common.exception.ozon.UserNotFoundException;
import org.ozonLabel.common.exception.user.ResourceNotFoundException;
import org.ozonLabel.common.exception.user.ValidationException;
import org.ozonLabel.common.service.user.CompanyService;
import org.ozonLabel.common.service.user.UserService;
import org.ozonLabel.ozonApi.entity.UserImage;
import org.ozonLabel.ozonApi.repository.UserImageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ImageService {

    private static final Set<String> ALLOWED_TYPES = Set.of(
            "image/png", "image/jpeg", "image/svg+xml", "image/webp"
    );
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5 MB

    private final UserImageRepository userImageRepository;
    private final FileStorageService fileStorageService;
    private final CompanyService companyService;
    private final UserService userService;

    @Transactional
    public ImageUploadResponseDto uploadImage(String userEmail, Long companyOwnerId, MultipartFile file) {
        companyService.checkAccess(userEmail, companyOwnerId);
        Long userId = getUserIdByEmail(userEmail);

        validateFile(file);

        String storedName = UUID.randomUUID() + getExtension(file.getOriginalFilename());
        String storagePath = fileStorageService.store(file, storedName, companyOwnerId);
        String url = fileStorageService.getPublicUrl(storagePath);

        UserImage image = UserImage.builder()
                .userId(userId)
                .companyId(companyOwnerId)
                .originalName(file.getOriginalFilename())
                .storedName(storedName)
                .mimeType(file.getContentType())
                .sizeBytes(file.getSize())
                .storagePath(storagePath)
                .url(url)
                .build();

        UserImage saved = userImageRepository.save(image);
        log.info("Загружено изображение id={} для пользователя {}", saved.getId(), userEmail);

        return ImageUploadResponseDto.builder()
                .id(saved.getId())
                .url(saved.getUrl())
                .originalName(saved.getOriginalName())
                .build();
    }

    @Transactional(readOnly = true)
    public UserImageListResponseDto getUserImages(String userEmail, Long companyOwnerId) {
        companyService.checkAccess(userEmail, companyOwnerId);
        Long userId = getUserIdByEmail(userEmail);

        List<UserImage> images = userImageRepository.findAllByCompanyIdAndUserId(companyOwnerId, userId);

        List<UserImageDto> dtos = images.stream()
                .map(this::toDto)
                .toList();

        return UserImageListResponseDto.builder()
                .images(dtos)
                .totalCount(dtos.size())
                .build();
    }

    @Transactional
    public void deleteImage(String userEmail, Long companyOwnerId, Long id) {
        companyService.checkAccess(userEmail, companyOwnerId);
        Long userId = getUserIdByEmail(userEmail);

        UserImage image = userImageRepository.findByIdAndCompanyIdAndUserId(id, companyOwnerId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Изображение с id=" + id));

        fileStorageService.delete(image.getStoragePath());
        userImageRepository.delete(image);
        log.info("Удалено изображение id={} пользователем {}", id, userEmail);
    }

    private Long getUserIdByEmail(String email) {
        UserResponseDto user = userService.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("Пользователь с email " + email + " не найден"));
        return user.getId();
    }

    private void validateFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new ValidationException("Файл не может быть пустым");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new ValidationException("Размер файла превышает 5 МБ");
        }
        if (!ALLOWED_TYPES.contains(file.getContentType())) {
            throw new ValidationException("Недопустимый тип файла. Разрешены: PNG, JPEG, SVG, WebP");
        }
    }

    private String getExtension(String filename) {
        if (filename == null) return "";
        int idx = filename.lastIndexOf('.');
        return idx > 0 ? filename.substring(idx) : "";
    }

    private UserImageDto toDto(UserImage image) {
        return UserImageDto.builder()
                .id(image.getId())
                .originalName(image.getOriginalName())
                .url(image.getUrl())
                .mimeType(image.getMimeType())
                .sizeBytes(image.getSizeBytes())
                .createdAt(image.getCreatedAt())
                .build();
    }
}
