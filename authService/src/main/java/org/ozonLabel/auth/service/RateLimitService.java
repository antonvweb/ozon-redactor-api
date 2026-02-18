package org.ozonLabel.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Rate limiting service for authentication operations
 */
@Service
@RequiredArgsConstructor
public class RateLimitService {

    private final StringRedisTemplate redisTemplate;

    private static final String LOGIN_ATTEMPTS_PREFIX = "login_attempts:";
    private static final String CODE_REQUESTS_PREFIX = "code_requests:";

    private static final int MAX_LOGIN_ATTEMPTS = 5;
    private static final int MAX_CODE_REQUESTS = 3;

    private static final long LOGIN_WINDOW_MINUTES = 15;
    private static final long CODE_WINDOW_MINUTES = 60;

    /**
     * Check if login attempt is allowed
     */
    public boolean allowLoginAttempt(String email) {
        String key = LOGIN_ATTEMPTS_PREFIX + email;
        String value = redisTemplate.opsForValue().get(key);

        if (value == null) {
            return true;
        }

        int attempts = Integer.parseInt(value);
        return attempts < MAX_LOGIN_ATTEMPTS;
    }

    /**
     * Record failed login attempt
     */
    public void recordFailedLogin(String email) {
        String key = LOGIN_ATTEMPTS_PREFIX + email;

        Long attempts = redisTemplate.opsForValue().increment(key);

        if (attempts != null && attempts == 1) {
            redisTemplate.expire(key, LOGIN_WINDOW_MINUTES, TimeUnit.MINUTES);
        }
    }

    /**
     * Clear login attempts after successful login
     */
    public void clearLoginAttempts(String email) {
        String key = LOGIN_ATTEMPTS_PREFIX + email;
        redisTemplate.delete(key);
    }

    /**
     * Check if code request is allowed
     */
    public boolean allowCodeRequest(String email) {
        String key = CODE_REQUESTS_PREFIX + email;
        String value = redisTemplate.opsForValue().get(key);

        if (value == null) {
            recordCodeRequest(email);
            return true;
        }

        int requests = Integer.parseInt(value);

        if (requests >= MAX_CODE_REQUESTS) {
            return false;
        }

        recordCodeRequest(email);
        return true;
    }

    /**
     * Record code request
     */
    private void recordCodeRequest(String email) {
        String key = CODE_REQUESTS_PREFIX + email;

        Long requests = redisTemplate.opsForValue().increment(key);

        if (requests != null && requests == 1) {
            redisTemplate.expire(key, CODE_WINDOW_MINUTES, TimeUnit.MINUTES);
        }
    }

    /**
     * Clear code requests after successful registration
     */
    public void clearCodeRequests(String email) {
        String key = CODE_REQUESTS_PREFIX + email;
        redisTemplate.delete(key);
    }
}
