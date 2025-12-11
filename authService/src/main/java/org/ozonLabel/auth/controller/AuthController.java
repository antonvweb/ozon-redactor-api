package org.ozonLabel.auth.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ozonLabel.auth.config.JwtService;
import org.ozonLabel.auth.dto.CreateAccountDto;
import org.ozonLabel.auth.dto.LoginRequestDto;
import org.ozonLabel.auth.dto.RequestCodeDto;
import org.ozonLabel.auth.exception.*;
import org.ozonLabel.auth.model.User;
import org.ozonLabel.auth.service.AuthService;
import org.ozonLabel.common.dto.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/request-code")
    public ResponseEntity<ApiResponse> requestCode(@Valid @RequestBody RequestCodeDto dto) {
        try {
            authService.requestVerificationCode(dto);
            return ResponseEntity.ok(
                    ApiResponse.success("Verification code sent to email")
            );
        } catch (UserAlreadyExistsException e) {
            log.warn("Registration attempt with existing email");
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (RateLimitExceededException e) {
            log.warn("Rate limit exceeded for code request");
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (InvalidVerificationCodeException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        } catch (EmailSendingException e) {
            log.error("Failed to send email", e);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ApiResponse.error("Не удалось отправить электронное письмо. Пожалуйста, попробуйте позже."));
        } catch (Exception e) {
            log.error("Unexpected error requesting verification code", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Произошла непредвиденная ошибка"));
        }
    }

    @PostMapping("/create-account")
    public ResponseEntity<ApiResponse> createAccount(@Valid @RequestBody CreateAccountDto dto) {
        try {
            User user = authService.createAccount(dto);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success("Аккаунт успешно создан", user.getId()));
        } catch (InvalidVerificationCodeException e) {
            log.warn("Invalid verification code provided");
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        } catch (UserAlreadyExistsException e) {
            log.warn("Account creation attempt with existing email");
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Error creating account", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Не удалось создать учетную запись"));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse> login(@Valid @RequestBody LoginRequestDto dto) {
        try {
            Map<String, String> tokens = authService.login(dto.getEmail(), dto.getPassword());
            return ResponseEntity.ok(ApiResponse.success("Вход успешен", tokens));
        } catch (InvalidCredentialsException e) {
            log.warn("Failed login attempt for email: {}", maskEmail(dto.getEmail()));
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (RateLimitExceededException e) {
            log.warn("Rate limit exceeded for login: {}", maskEmail(dto.getEmail()));
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error during login", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Произошла непредвиденная ошибка"));
        }
    }

    @PostMapping("/refresh-token")
    public ResponseEntity<ApiResponse> refreshToken(
            @CookieValue(value = "refreshToken", required = false) String refreshToken,
            @RequestHeader(value = "X-Refresh-Token", required = false) String headerRefreshToken) {

        // Try cookie first, then header
        String token = refreshToken != null ? refreshToken : headerRefreshToken;

        if (token == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Отсутствует токен обновления."));
        }

        try {
            Map<String, String> tokens = authService.refreshToken(token);
            return ResponseEntity.ok(
                    ApiResponse.success("Токен успешно обновлен", tokens)
            );
        } catch (InvalidRefreshTokenException e) {
            log.warn("Invalid refresh token provided");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Error refreshing token", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Не удалось обновить токен."));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse> logout(Authentication auth) {
        if (auth != null && auth.isAuthenticated()) {
            try {
                authService.logout(auth.getName());
                return ResponseEntity.ok(
                        ApiResponse.success("Вышел из системы успешно")
                );
            } catch (Exception e) {
                log.error("Error during logout", e);
            }
        }

        return ResponseEntity.ok(
                ApiResponse.success("Вышел из системы успешно")
        );
    }

    /**
     * Mask email for logging (privacy)
     */
    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return "***";
        }
        String[] parts = email.split("@");
        String localPart = parts[0];
        String domain = parts[1];

        if (localPart.length() <= 2) {
            return "**@" + domain;
        }

        return localPart.substring(0, 2) + "***@" + domain;
    }
}