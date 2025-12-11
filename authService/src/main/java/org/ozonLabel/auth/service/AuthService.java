package org.ozonLabel.auth.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ozonLabel.auth.config.JwtService;
import org.ozonLabel.auth.dto.CreateAccountDto;
import org.ozonLabel.auth.dto.RequestCodeDto;
import org.ozonLabel.auth.exception.*;
import org.ozonLabel.auth.model.User;
import org.ozonLabel.auth.repository.UserRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final VerificationCodeService verificationCodeService;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;
    private final JwtService jwtService;
    private final RateLimitService rateLimitService;

    private static final int MAX_LOGIN_ATTEMPTS = 5;
    private static final int MAX_CODE_REQUESTS = 3;

    /**
     * Request verification code with rate limiting
     */
    @Transactional
    public void requestVerificationCode(RequestCodeDto dto) {
        // Rate limiting check
        if (!rateLimitService.allowCodeRequest(dto.getEmail())) {
            throw new RateLimitExceededException(
                    "Слишком много запросов кода. Пожалуйста, попробуйте позже.");
        }

        // Validate passwords match
        if (!dto.getPassword().equals(dto.getConfirmPassword())) {
            throw new InvalidVerificationCodeException("Пароли не совпадают");
        }

        // Check if user exists (unified error message to prevent email enumeration)
        if (userRepository.existsByEmail(dto.getEmail())) {
            throw new UserAlreadyExistsException();
        }

        try {
            // Generate and save code
            String code = verificationCodeService.generateAndSaveCode(
                    dto.getEmail(),
                    dto.getName(),
                    dto.getPassword()
            );

            // Send email
            emailService.sendVerificationCode(dto.getEmail(), code, dto.getName());

            log.info("Verification code sent to: {}", maskEmail(dto.getEmail()));
        } catch (EmailSendingException e) {
            log.error("Failed to send verification code to: {}", maskEmail(dto.getEmail()));
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error requesting verification code", e);
            throw new AuthException("Не удалось отправить код подтверждения.", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Create account after code verification
     */
    @Transactional
    @CacheEvict(value = {"userProfiles", "userCompanyAccess"}, allEntries = true)
    public User createAccount(CreateAccountDto dto) {
        // Get stored data from Redis
        String storedData = verificationCodeService.getStoredData(dto.getEmail());

        if (storedData == null) {
            throw new InvalidVerificationCodeException(
                    "Код подтверждения истек или не найден");
        }

        try {
            // Parse JSON from Redis
            JsonNode jsonNode = objectMapper.readTree(storedData);
            String storedCode = jsonNode.get("code").asText();
            String name = jsonNode.get("name").asText();
            String password = jsonNode.get("password").asText();

            // Verify code
            if (!storedCode.equals(dto.getCode())) {
                throw new InvalidVerificationCodeException("Неверный код подтверждения");
            }

            // Double-check user doesn't exist (race condition protection)
            if (userRepository.existsByEmail(dto.getEmail())) {
                throw new UserAlreadyExistsException();
            }

            // Create user
            User user = User.builder()
                    .name(name)
                    .email(dto.getEmail().toLowerCase().trim()) // Normalize email
                    .passwordHash(passwordEncoder.encode(password))
                    .companyName(dto.getCompanyName())
                    .inn(dto.getInn())
                    .phone(dto.getPhone())
                    .subscription(User.SubscriptionType.FREE)
                    .ozonClientId(dto.getOzonClientId())
                    .ozonApiKey(dto.getOzonApiKey())
                    .loginAttempts(0)
                    .build();

            User savedUser = userRepository.save(user);

            // Delete code from Redis
            verificationCodeService.deleteCode(dto.getEmail());

            // Clear rate limit after successful registration
            rateLimitService.clearCodeRequests(dto.getEmail());

            log.info("Account created for user: {}", maskEmail(dto.getEmail()));
            return savedUser;

        } catch (InvalidVerificationCodeException | UserAlreadyExistsException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error creating account", e);
            throw new AuthException("Не удалось создать учетную запись", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Login with rate limiting and account lockout
     */
    @Transactional
    public Map<String, String> login(String email, String password) {
        String normalizedEmail = email.toLowerCase().trim();

        // Rate limiting check
        if (!rateLimitService.allowLoginAttempt(normalizedEmail)) {
            throw new RateLimitExceededException(
                    "Слишком много попыток входа. Пожалуйста, попробуйте позже.");
        }

        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(InvalidCredentialsException::new);

        // Check if account is locked
        if (user.isAccountLocked()) {
            log.warn("Login attempt on locked account: {}", maskEmail(normalizedEmail));
            throw new RateLimitExceededException(
                    "Учетная запись временно заблокирована. Пожалуйста, попробуйте позже.");
        }

        // Verify password
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            handleFailedLogin(user);
            throw new InvalidCredentialsException();
        }

        // Reset failed attempts on successful login
        if (user.getLoginAttempts() > 0) {
            user.setLoginAttempts(0);
            user.setAccountLockedUntil(null);
        }

        // Generate tokens
        String accessToken = jwtService.generateToken(user.getEmail());
        String refreshToken = jwtService.generateRefreshToken(user.getEmail());

        // Save refresh token
        user.setRefreshToken(refreshToken);
        user.setRefreshTokenExpiresAt(LocalDateTime.now().plusDays(14));
        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);

        // Clear rate limit on successful login
        rateLimitService.clearLoginAttempts(normalizedEmail);

        log.info("User logged in: {}", maskEmail(normalizedEmail));

        return Map.of(
                "accessToken", accessToken,
                "refreshToken", refreshToken
        );
    }

    /**
     * Refresh access token
     */
    @Transactional
    public Map<String, String> refreshToken(String refreshToken) {
        if (refreshToken == null || refreshToken.trim().isEmpty()) {
            throw new InvalidRefreshTokenException();
        }

        try {
            String email = jwtService.extractEmail(refreshToken);
            User user = userRepository.findByEmail(email)
                    .orElseThrow(InvalidRefreshTokenException::new);

            // Validate refresh token
            if (!refreshToken.equals(user.getRefreshToken()) ||
                    user.getRefreshTokenExpiresAt() == null ||
                    user.getRefreshTokenExpiresAt().isBefore(LocalDateTime.now())) {
                throw new InvalidRefreshTokenException();
            }

            // Generate new tokens (token rotation for security)
            String newAccessToken = jwtService.generateToken(email);
            String newRefreshToken = jwtService.generateRefreshToken(email);

            // Update refresh token in database
            user.setRefreshToken(newRefreshToken);
            user.setRefreshTokenExpiresAt(LocalDateTime.now().plusDays(14));
            userRepository.save(user);

            log.debug("Token refreshed for user: {}", maskEmail(email));

            return Map.of(
                    "accessToken", newAccessToken,
                    "refreshToken", newRefreshToken
            );

        } catch (InvalidRefreshTokenException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error refreshing token", e);
            throw new InvalidRefreshTokenException();
        }
    }

    /**
     * Logout user by invalidating refresh token
     */
    @Transactional
    public void logout(String email) {
        userRepository.findByEmail(email).ifPresent(user -> {
            user.setRefreshToken(null);
            user.setRefreshTokenExpiresAt(null);
            userRepository.save(user);
            log.info("User logged out: {}", maskEmail(email));
        });
    }

    // Private helper methods

    private void handleFailedLogin(User user) {
        int attempts = user.getLoginAttempts() + 1;
        user.setLoginAttempts(attempts);

        if (attempts >= MAX_LOGIN_ATTEMPTS) {
            user.setAccountLockedUntil(LocalDateTime.now().plusMinutes(30));
            log.warn("Account locked due to failed login attempts: {}", maskEmail(user.getEmail()));
        }

        userRepository.save(user);
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