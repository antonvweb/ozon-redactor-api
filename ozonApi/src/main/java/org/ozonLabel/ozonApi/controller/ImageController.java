package org.ozonLabel.ozonApi.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ozonLabel.common.dto.ApiResponse;
import org.ozonLabel.common.dto.label.ImageUploadResponseDto;
import org.ozonLabel.common.dto.label.UserImageListResponseDto;
import org.ozonLabel.ozonApi.service.ImageService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/images")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Slf4j
public class ImageController {

    private final ImageService imageService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImageUploadResponseDto> uploadImage(
            @RequestParam Long companyOwnerId,
            @RequestParam("file") MultipartFile file,
            Authentication auth) {

        String userEmail = auth.getName();
        log.info("Загрузка изображения {} для компании {} пользователем {}",
                file.getOriginalFilename(), companyOwnerId, userEmail);

        ImageUploadResponseDto response = imageService.uploadImage(userEmail, companyOwnerId, file);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<UserImageListResponseDto> getUserImages(
            @RequestParam Long companyOwnerId,
            Authentication auth) {

        String userEmail = auth.getName();
        log.info("Получение списка изображений для компании {} пользователем {}", companyOwnerId, userEmail);

        UserImageListResponseDto response = imageService.getUserImages(userEmail, companyOwnerId);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse> deleteImage(
            @PathVariable Long id,
            @RequestParam Long companyOwnerId,
            Authentication auth) {

        String userEmail = auth.getName();
        log.info("Удаление изображения {} компании {} пользователем {}", id, companyOwnerId, userEmail);

        imageService.deleteImage(userEmail, companyOwnerId, id);
        return ResponseEntity.ok(ApiResponse.success("Изображение удалено"));
    }
}
