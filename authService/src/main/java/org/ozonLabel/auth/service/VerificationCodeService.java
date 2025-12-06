package org.ozonLabel.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class VerificationCodeService {
    
    private final StringRedisTemplate redisTemplate;
    private static final String CODE_PREFIX = "verification:";
    private static final int CODE_LENGTH = 6;
    private static final long CODE_EXPIRATION_MINUTES = 10;
    
    public String generateAndSaveCode(String email, String name, String password) {
        String code = generateCode();
        String key = CODE_PREFIX + email;
        
        // Сохраняем код, имя и пароль в Redis как JSON строку
        String value = String.format("{\"code\":\"%s\",\"name\":\"%s\",\"password\":\"%s\"}", 
                                      code, name, password);
        
        redisTemplate.opsForValue().set(key, value, CODE_EXPIRATION_MINUTES, TimeUnit.MINUTES);
        
        return code;
    }
    
    public String getStoredData(String email) {
        String key = CODE_PREFIX + email;
        return redisTemplate.opsForValue().get(key);
    }
    
    public void deleteCode(String email) {
        String key = CODE_PREFIX + email;
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
