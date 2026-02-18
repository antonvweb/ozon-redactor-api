package org.ozonLabel.auth.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class VerificationCodeService {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private static final String CODE_PREFIX = "verification:";
    private static final int CODE_LENGTH = 6;
    private static final long CODE_EXPIRATION_MINUTES = 10;

    public String generateAndSaveCode(String email, String name, String password) {
        String code = generateCode();
        String key = CODE_PREFIX + normalizeEmail(email);

        try {
            // Безопасная сериализация JSON через ObjectMapper
            Map<String, String> data = Map.of(
                    "code", code,
                    "name", name,
                    "password", password
            );
            String value = objectMapper.writeValueAsString(data);

            redisTemplate.opsForValue().set(key, value, CODE_EXPIRATION_MINUTES, TimeUnit.MINUTES);

            return code;
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize verification data", e);
            throw new RuntimeException("Failed to save verification code", e);
        }
    }

    private String normalizeEmail(String email) {
        return email.toLowerCase().trim();
    }
    
    public String getStoredData(String email) {
        String key = CODE_PREFIX + normalizeEmail(email);
        return redisTemplate.opsForValue().get(key);
    }

    public void deleteCode(String email) {
        String key = CODE_PREFIX + normalizeEmail(email);
        redisTemplate.delete(key);
    }
    
    private String generateCode() {
        SecureRandom random = new SecureRandom();
        StringBuilder code = new StringBuilder();
        
        for (int i = 0; i < CODE_LENGTH; i++) {
            code.append(random.nextInt(10));
        }
        
        return code.toString();
    }
}
