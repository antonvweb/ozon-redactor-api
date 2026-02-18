package org.ozonLabel.auth.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ozonLabel.common.dto.auth.CreateAccountDto;
import org.ozonLabel.common.dto.auth.LoginRequestDto;
import org.ozonLabel.common.dto.auth.RequestCodeDto;
import org.ozonLabel.auth.model.User;
import org.ozonLabel.auth.service.AuthService;
import org.ozonLabel.auth.service.CookieService;
import org.ozonLabel.auth.service.CsrfTokenService;
import org.ozonLabel.common.dto.ApiResponse;
import org.ozonLabel.common.exception.auth.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final AuthService authService;
    private final CookieService cookieService;
    private final CsrfTokenService csrfTokenService;

    @PostMapping("/request-code")
    public ResponseEntity<ApiResponse> requestCode(@Valid @RequestBody RequestCodeDto dto) {
        try {
            authService.requestVerificationCode(dto);
            return ResponseEntity.ok(
                    ApiResponse.success("Код подтверждения отправлен на email")
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

    /**
     * Login endpoint - sets tokens in HTTP-only cookies
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse> login(
            @Valid @RequestBody LoginRequestDto dto,
            HttpServletResponse response) {
        try {
            Map<String, String> tokens = authService.login(dto.getEmail(), dto.getPassword());

            String accessToken = tokens.get("accessToken");
            String refreshToken = tokens.get("refreshToken");

            // Generate CSRF token
            String csrfToken = csrfTokenService.generateToken(dto.getEmail());

            // Set tokens in secure HTTP-only cookies
            cookieService.addCookiesToResponse(response, accessToken, refreshToken, csrfToken);

            log.info("User logged in successfully: {}", maskEmail(dto.getEmail()));

            // Return only non-sensitive data (no tokens in body)
            return ResponseEntity.ok(ApiResponse.success("Вход успешен", Map.of(
                    "csrfToken", csrfToken // Frontend needs this for subsequent requests
            )));

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

    /**
     * Refresh token endpoint - reads refresh token from cookie
     */
    @PostMapping("/refresh-token")
    public ResponseEntity<ApiResponse> refreshToken(
            HttpServletRequest request,
            HttpServletResponse response) {

        // Extract refresh token from cookie
        String refreshToken = cookieService.extractRefreshToken(request).orElse(null);

        if (refreshToken == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Отсутствует токен обновления."));
        }

        try {
            Map<String, String> tokens = authService.refreshToken(refreshToken);

            String newAccessToken = tokens.get("accessToken");
            String newRefreshToken = tokens.get("refreshToken");
            String userEmail = tokens.get("email");

            // Generate new CSRF token
            String csrfToken = csrfTokenService.refreshToken(userEmail);

            // Update cookies
            cookieService.addCookiesToResponse(response, newAccessToken, newRefreshToken, csrfToken);

            return ResponseEntity.ok(ApiResponse.success("Токен успешно обновлен", Map.of(
                    "csrfToken", csrfToken
            )));

        } catch (InvalidRefreshTokenException e) {
            log.warn("Invalid refresh token provided");
            cookieService.clearAuthCookies(response);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Error refreshing token", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Не удалось обновить токен."));
        }
    }

    /**
     * Logout endpoint - clears all auth cookies
     */
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse> logout(
            Authentication auth,
            HttpServletResponse response) {

        if (auth != null && auth.isAuthenticated()) {
            try {
                String userEmail = auth.getName();
                authService.logout(userEmail);
                csrfTokenService.invalidateToken(userEmail);
                log.info("User logged out: {}", maskEmail(userEmail));
            } catch (Exception e) {
                log.error("Error during logout", e);
            }
        }

        // Always clear cookies
        cookieService.clearAuthCookies(response);

        return ResponseEntity.ok(ApiResponse.success("Вы успешно вышли из системы"));
    }

    /**
     * Check authentication status
     */
    @GetMapping("/status")
    public ResponseEntity<ApiResponse> checkAuthStatus(Authentication auth) {
        if (auth != null && auth.isAuthenticated()) {
            return ResponseEntity.ok(ApiResponse.success("Authenticated", Map.of(
                    "authenticated", true,
                    "email", auth.getName()
            )));
        }
        return ResponseEntity.ok(ApiResponse.success("Not authenticated", Map.of(
                "authenticated", false
        )));
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
