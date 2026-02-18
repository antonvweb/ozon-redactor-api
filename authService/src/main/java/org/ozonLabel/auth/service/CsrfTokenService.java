package org.ozonLabel.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

/**
 * CSRF Token Service using Redis for storage
 * Implements double-submit cookie pattern
 */
@Service
@RequiredArgsConstructor
public class CsrfTokenService {

    private final StringRedisTemplate redisTemplate;

    private static final String CSRF_PREFIX = "csrf:";
    private static final int TOKEN_LENGTH = 32;
    private static final long TOKEN_EXPIRATION_HOURS = 24;

    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Generate a new CSRF token and store it associated with user email
     */
    public String generateToken(String userEmail) {
        byte[] randomBytes = new byte[TOKEN_LENGTH];
        secureRandom.nextBytes(randomBytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);

        String key = CSRF_PREFIX + userEmail;
        redisTemplate.opsForValue().set(key, token, TOKEN_EXPIRATION_HOURS, TimeUnit.HOURS);

        return token;
    }

    /**
     * Validate CSRF token for a user
     */
    public boolean validateToken(String userEmail, String token) {
        if (userEmail == null || token == null || token.isEmpty()) {
            return false;
        }

        String key = CSRF_PREFIX + userEmail;
        String storedToken = redisTemplate.opsForValue().get(key);

        return token.equals(storedToken);
    }

    /**
     * Invalidate CSRF token on logout
     */
    public void invalidateToken(String userEmail) {
        String key = CSRF_PREFIX + userEmail;
        redisTemplate.delete(key);
    }

    /**
     * Refresh CSRF token (generate new one)
     */
    public String refreshToken(String userEmail) {
        invalidateToken(userEmail);
        return generateToken(userEmail);
    }
}
