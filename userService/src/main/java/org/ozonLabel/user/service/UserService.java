package org.ozonLabel.user.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ozonLabel.common.exception.ResourceNotFoundException;
import org.ozonLabel.common.exception.ValidationException;
import org.ozonLabel.user.dto.PremiumRequestDto;
import org.ozonLabel.user.dto.UpdateOzonCredentialsDto;
import org.ozonLabel.user.dto.UpdateProfileDto;
import org.ozonLabel.user.dto.UserResponseDto;
import org.ozonLabel.domain.model.User;
import org.ozonLabel.domain.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {
    private final UserRepository userRepository;
    private final JavaMailSender mailSender;

    @Value("${app.support.email:a.volkov@print-365.ru}")
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
        if (dto.getOzonClientId() != null) user.setOzonClientId(dto.getOzonClientId().trim());
        if (dto.getOzonApiKey() != null) user.setOzonApiKey(dto.getOzonApiKey().trim());

        User saved = userRepository.save(user);
        log.info("Ozon credentials updated for user: {}", email);

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
        dto.setOzonClientId(user.getOzonClientId());
        dto.setSubscription(user.getSubscription());
        return dto;
    }
}