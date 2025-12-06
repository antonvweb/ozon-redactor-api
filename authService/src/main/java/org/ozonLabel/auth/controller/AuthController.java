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
import org.ozonLabel.auth.model.User;
import org.ozonLabel.auth.repository.UserRepository;
import org.ozonLabel.auth.service.AuthService;
import org.ozonLabel.common.dto.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class AuthController {
    
    private final AuthService authService;
    private final JwtService jwtService;
    private final UserRepository userRepository;

    @PostMapping("/request-code")
    public ResponseEntity<ApiResponse> requestCode(@Valid @RequestBody RequestCodeDto dto,
                                                   BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            String errors = bindingResult.getAllErrors().stream()
                .map(error -> error.getDefaultMessage())
                .collect(Collectors.joining(", "));
            return ResponseEntity.badRequest()
                .body(ApiResponse.error(errors));
        }
        
        try {
            authService.requestVerificationCode(dto);
            return ResponseEntity.ok(
                ApiResponse.success("Код подтверждения отправлен на email")
            );
        } catch (IllegalArgumentException e) {
            log.error("Validation error: {}", e.getMessage());
            return ResponseEntity.badRequest()
                .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Error requesting verification code", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("Произошла ошибка при отправке кода"));
        }
    }
    
    @PostMapping("/create-account")
    public ResponseEntity<ApiResponse> createAccount(@Valid @RequestBody CreateAccountDto dto,
                                                      BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            String errors = bindingResult.getAllErrors().stream()
                .map(error -> error.getDefaultMessage())
                .collect(Collectors.joining(", "));
            return ResponseEntity.badRequest()
                .body(ApiResponse.error(errors));
        }
        
        try {
            User user = authService.createAccount(dto);
            return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Аккаунт успешно создан", user.getId()));
        } catch (IllegalArgumentException e) {
            log.error("Validation error: {}", e.getMessage());
            return ResponseEntity.badRequest()
                .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Error creating account", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("Произошла ошибка при создании аккаунта"));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse> login(@Valid @RequestBody LoginRequestDto dto,
                                             BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            String errors = bindingResult.getAllErrors().stream()
                    .map(ObjectError::getDefaultMessage)
                    .collect(Collectors.joining(", "));
            return ResponseEntity.badRequest().body(ApiResponse.error(errors));
        }

        try {
            Map<String, String> tokens = authService.login(dto.getEmail(), dto.getPassword());
            return ResponseEntity.ok(ApiResponse.success("Успешный вход", tokens));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/refresh-token")
    public ResponseEntity<ApiResponse> refreshToken(@CookieValue(value = "refreshToken", required = false) String refreshToken,
                                                    HttpServletRequest request, HttpServletResponse response) {
        if (refreshToken == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Refresh token отсутствует"));
        }

        try {
            String email = jwtService.extractEmail(refreshToken);
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден"));

            // Проверка, что токен совпадает с сохранённым и не истёк
            if (!refreshToken.equals(user.getRefreshToken()) ||
                    user.getRefreshTokenExpiresAt().isBefore(LocalDateTime.now())) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.error("Недействительны refresh token"));
            }

            String newAccessToken = jwtService.generateToken(email);
            String newRefreshToken = jwtService.generateRefreshToken(email);

            // Обновляем refresh token (ротация — это хорошо для безопасности)
            user.setRefreshToken(newRefreshToken);
            user.setRefreshTokenExpiresAt(LocalDateTime.now().plusDays(14));
            userRepository.save(user);

            return ResponseEntity.ok(ApiResponse.success("Токен обновлён",
                    Map.of("accessToken", newAccessToken, "refreshToken", newRefreshToken)));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Недействительный refresh token"));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse> logout(HttpServletResponse response) {
        // Лучше: найди пользователя по текущему токену и обнули refreshToken в БД
        return ResponseEntity.ok(ApiResponse.success("Выход выполнен"));
    }
}
