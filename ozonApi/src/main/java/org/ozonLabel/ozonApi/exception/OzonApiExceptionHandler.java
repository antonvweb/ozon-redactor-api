package org.ozonLabel.ozonApi.exception;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ozonLabel.common.exception.ozon.*;
import org.ozonLabel.common.exception.user.BusinessException;
import org.ozonLabel.common.exception.user.ConflictException;
import org.ozonLabel.common.exception.user.ResourceNotFoundException;
import org.ozonLabel.common.exception.user.ValidationException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.time.LocalDateTime;

@RestControllerAdvice(basePackages = "org.ozonLabel.ozonApi")
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class OzonApiExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFound(ResourceNotFoundException ex) {
        log.warn("Resource not found: {}", ex.getMessage());

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.NOT_FOUND.value())
                .error("Не найдено")
                .message(ex.getMessage())
                .requiresSettings(false)
                .build();
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    @ExceptionHandler(OzonApiCredentialsMissingException.class)
    public ResponseEntity<ErrorResponse> handleOzonApiCredentialsMissing(OzonApiCredentialsMissingException ex) {
        log.warn("Missing Ozon API credentials: {}", ex.getMessage());

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Отсутствуют учетные данные.")
                .message("Требуются учетные данные API Ozon. Пожалуйста, добавьте Client-Id и Api-Key в настройки.")
                .requiresSettings(true)
                .build();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleUserNotFound(UserNotFoundException ex) {
        log.warn("User not found: {}", ex.getMessage());

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.NOT_FOUND.value())
                .error("Не найдено")
                .message("Пользователь не найден")
                .requiresSettings(false)
                .build();
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    @ExceptionHandler(FolderNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleFolderNotFound(FolderNotFoundException ex) {
        log.warn("Folder not found: {}", ex.getMessage());

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.NOT_FOUND.value())
                .error("Не найдено")
                .message("Папка не найдена")
                .requiresSettings(false)
                .build();
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    @ExceptionHandler(FolderAccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleFolderAccessDenied(FolderAccessDeniedException ex) {
        log.warn("Folder access denied: {}", ex.getMessage());

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.FORBIDDEN.value())
                .error("Доступ запрещен")
                .message("У вас нет доступа к этой папке.")
                .requiresSettings(false)
                .build();
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
    }

    @ExceptionHandler(InvalidFolderOperationException.class)
    public ResponseEntity<ErrorResponse> handleInvalidFolderOperation(InvalidFolderOperationException ex) {
        log.warn("Invalid folder operation: {}", ex.getMessage());

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Неверная операция")
                .message(ex.getMessage())
                .requiresSettings(false)
                .build();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(OzonApiException.class)
    public ResponseEntity<ErrorResponse> handleOzonApiException(OzonApiException ex) {
        log.error("Ошибка API Ozon: {}", ex.getMessage(), ex);

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(ex.getStatus().value())
                .error("Ошибка API Ozon")
                .message("Ошибка связи с API Ozon. Пожалуйста, попробуйте позже.")
                .requiresSettings(false)
                .build();
        return ResponseEntity.status(ex.getStatus()).body(error);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException ex) {
        log.warn("File upload size exceeded: {}", ex.getMessage());

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.PAYLOAD_TOO_LARGE.value())
                .error("Файл слишком большой")
                .message("Размер файла превышает максимально допустимый размер.")
                .requiresSettings(false)
                .build();
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(error);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Invalid argument: {}", ex.getMessage());

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Неверный запрос")
                .message(ex.getMessage())
                .requiresSettings(false)
                .build();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ErrorResponse> handleValidation(ValidationException ex) {
        log.warn("Validation error: {}", ex.getMessage());

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Ошибка валидации")
                .message(ex.getMessage())
                .requiresSettings(false)
                .build();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflict(ConflictException ex) {
        log.warn("Conflict: {}", ex.getMessage());

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.CONFLICT.value())
                .error("Конфликт")
                .message(ex.getMessage())
                .requiresSettings(false)
                .build();
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException ex) {
        log.warn("Business exception: {}", ex.getMessage());

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(ex.getStatus().value())
                .error("Ошибка бизнес-логики")
                .message(ex.getMessage())
                .requiresSettings(false)
                .build();
        return ResponseEntity.status(ex.getStatus()).body(error);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        log.error("Unexpected error occurred", ex);

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .error("Внутренняя ошибка сервера")
                .message("Произошла непредвиденная ошибка. Пожалуйста, попробуйте позже.")
                .requiresSettings(false)
                .build();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ErrorResponse {
        private LocalDateTime timestamp;
        private Integer status;
        private String error;
        private String message;
        private Boolean requiresSettings;
    }
}
