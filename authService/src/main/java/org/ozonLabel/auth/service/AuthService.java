package org.ozonLabel.auth.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.ozonLabel.auth.config.JwtService;
import org.ozonLabel.auth.dto.CreateAccountDto;
import org.ozonLabel.auth.dto.RequestCodeDto;
import org.ozonLabel.auth.model.User;
import org.ozonLabel.auth.repository.UserRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuthService {
    
    private final UserRepository userRepository;
    private final VerificationCodeService verificationCodeService;
    private final EmailService emailService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final JwtService jwtService;
    
    public void requestVerificationCode(RequestCodeDto dto) {
        // Проверка совпадения паролей
        if (!dto.getPassword().equals(dto.getConfirmPassword())) {
            throw new IllegalArgumentException("Пароли не совпадают");
        }
        
        // Проверка существования пользователя
        if (userRepository.existsByEmail(dto.getEmail())) {
            throw new IllegalArgumentException("Пользователь с таким email уже существует");
        }
        
        // Генерация и сохранение кода
        String code = verificationCodeService.generateAndSaveCode(
            dto.getEmail(), 
            dto.getName(), 
            dto.getPassword()
        );
        
        // Отправка кода на email
        emailService.sendVerificationCode(dto.getEmail(), code, dto.getName());
    }
    
    @Transactional
    public User createAccount(CreateAccountDto dto) {
        // Получение сохраненных данных из Redis
        String storedData = verificationCodeService.getStoredData(dto.getEmail());
        
        if (storedData == null) {
            throw new IllegalArgumentException("Код подтверждения истек или не найден");
        }
        
        try {
            // Парсинг JSON из Redis
            JsonNode jsonNode = objectMapper.readTree(storedData);
            String storedCode = jsonNode.get("code").asText();
            String name = jsonNode.get("name").asText();
            String password = jsonNode.get("password").asText();
            
            // Проверка кода
            if (!storedCode.equals(dto.getCode())) {
                throw new IllegalArgumentException("Неверный код подтверждения");
            }
            
            // Проверка существования пользователя (двойная проверка)
            if (userRepository.existsByEmail(dto.getEmail())) {
                throw new IllegalArgumentException("Пользователь с таким email уже существует");
            }
            
            // Создание пользователя
            User user = User.builder()
                .name(name)
                .email(dto.getEmail())
                .passwordHash(passwordEncoder.encode(password))
                .companyName(dto.getCompanyName())
                .inn(dto.getInn())
                .phone(dto.getPhone())
                .subscription(User.SubscriptionType.FREE)
                .ozonClientId(dto.getOzonClientId())
                .ozonApiKey(dto.getOzonApiKey())
                .build();
            
            User savedUser = userRepository.save(user);
            
            // Удаление кода из Redis
            verificationCodeService.deleteCode(dto.getEmail());
            
            return savedUser;
            
        } catch (Exception e) {
            throw new RuntimeException("Ошибка при создании аккаунта", e);
        }
    }

    public Map<String, String> login(String email, String password) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Неверный email или пароль"));

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new IllegalArgumentException("Неверный email или пароль");
        }

        String accessToken = jwtService.generateToken(user.getEmail());
        String refreshToken = jwtService.generateRefreshToken(user.getEmail());

        // Сохраняем refresh token в БД
        user.setRefreshToken(refreshToken);
        user.setRefreshTokenExpiresAt(LocalDateTime.now().plusDays(14));
        userRepository.save(user);

        return Map.of(
                "accessToken", accessToken,
                "refreshToken", refreshToken
        );
    }
}
