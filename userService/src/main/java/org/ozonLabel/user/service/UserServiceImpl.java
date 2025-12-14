package org.ozonLabel.user.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ozonLabel.common.exception.user.ResourceNotFoundException;
import org.ozonLabel.common.exception.user.ValidationException;
import org.ozonLabel.common.dto.user.PremiumRequestDto;
import org.ozonLabel.common.dto.user.UpdateOzonCredentialsDto;
import org.ozonLabel.common.dto.user.UpdateProfileDto;
import org.ozonLabel.common.dto.user.UserResponseDto;
import org.ozonLabel.common.service.user.UserService;
import org.ozonLabel.user.entity.User;
import org.ozonLabel.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserServiceImpl implements UserService {
    private final UserRepository userRepository;
    private final JavaMailSender mailSender;

    @Value("${app.support.email:zhelonkin.zakhar@yandex.ru}")
    private String supportEmail;

    @Cacheable(value = "userProfiles", key = "#email")
    public UserResponseDto getCurrentUser(String email) {
        log.debug("Fetching user data for: {}", email);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User"));

        return mapToDto(user);
    }

    @Transactional
    @CacheEvict(value = {"userProfiles", "userCompanyAccess"}, key = "#currentEmail")
    public UserResponseDto updateProfile(String currentEmail, UpdateProfileDto dto) {
        if (dto.isEmpty()) {
            throw new ValidationException("Необходимо указать как минимум одно поле.");
        }

        User user = getUserByEmail(currentEmail);

        if (dto.getCompanyName() != null) user.setCompanyName(dto.getCompanyName());
        if (dto.getInn() != null) user.setInn(dto.getInn());
        if (dto.getPhone() != null) user.setPhone(dto.getPhone());
        if (dto.getEmail() != null) {
            if (userRepository.existsByEmail(dto.getEmail()) && !dto.getEmail().equals(currentEmail)) {
                throw new ValidationException("Этот адрес электронной почты уже занят.");
            }
            user.setEmail(dto.getEmail());
        }

        User saved = userRepository.save(user);
        log.info("User profile updated: {}", currentEmail);

        return mapToDto(saved);
    }

    @Transactional
    @CacheEvict(value = "userProfiles", key = "#email")
    public UserResponseDto updateOzonCredentials(String email, UpdateOzonCredentialsDto dto) {
        if (dto.isEmpty()) {
            throw new ValidationException("Необходимо указать как минимум одно поле.");
        }

        User user = getUserByEmail(email);

        // Разрешаем передать пустую строку — это значит "удалить"
        if (dto.getOzonClientId() != null) {
            String trimmed = dto.getOzonClientId().trim();
            user.setOzonClientId(trimmed.isEmpty() ? null : trimmed);
        }
        if (dto.getOzonApiKey() != null) {
            String trimmed = dto.getOzonApiKey().trim();
            user.setOzonApiKey(trimmed.isEmpty() ? null : trimmed);
        }

        User saved = userRepository.save(user);
        log.info("Ozon credentials updated (hidden in response) for user: {}", email);

        return mapToDto(saved);
    }

    public void requestPremiumAccess(String requesterEmail, PremiumRequestDto dto) {
        User requester = userRepository.findByEmail(requesterEmail)
                .orElse(null);

        String subject = "Premium Subscription Request — ozonLabel";
        String text = """
                New premium subscription request!

                Contact email: %s
                User: %s
                System ID: %s
                Current subscription: %s

                Please contact them as soon as possible!
                """.formatted(
                dto.getEmail(),
                requester != null ? requester.getName() + " (" + requester.getEmail() + ")" : "Not registered",
                requester != null ? requester.getId() : "—",
                requester != null ? requester.getSubscription() : "—"
        );

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom("a.volkov@print-365.ru");
        message.setTo(supportEmail);
        message.setSubject(subject);
        message.setText(text);

        mailSender.send(message);
        log.info("Premium request sent to {} from {}", supportEmail, dto.getEmail());
    }

    @Override
    public Optional<UserResponseDto> findByEmail(String email) {
        return userRepository.findByEmail(email)
                .map(this::mapToDto);
    }

    @Override
    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }

    @Override
    public Optional<UserResponseDto> findByEmailCaseInsensitive(String email) {
        return userRepository.findByEmailCaseInsensitive(email)
                .map(this::mapToDto);
    }

    @Override
    public Optional<UserResponseDto> findByPhone(String phone) {
        return userRepository.findByPhone(phone)
                .map(this::mapToDto);
    }

    @Override
    public Optional<UserResponseDto> findById(Long id) {
        return userRepository.findById(id)
                .map(this::mapToDto);
    }

    private User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User"));
    }

    private UserResponseDto mapToDto(User user) {
        UserResponseDto dto = new UserResponseDto();
        dto.setId(user.getId());
        dto.setName(user.getName());
        dto.setEmail(user.getEmail());
        dto.setCompanyName(user.getCompanyName());
        dto.setInn(user.getInn());
        dto.setPhone(user.getPhone());
        dto.setSubscription((user.getSubscription()));
        dto.setEmailVerified(user.getEmailVerified());
        dto.setPasswordChangedAt(user.getPasswordChangedAt());

        dto.setHasOzonClientId(user.getOzonClientId() != null && !user.getOzonClientId().trim().isEmpty());
        dto.setHasOzonApiKey(user.getOzonApiKey() != null && !user.getOzonApiKey().trim().isEmpty());

        dto.setSubscription(user.getSubscription());
        return dto;
    }
}