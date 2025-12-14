package org.ozonLabel.common.service.user;

import org.ozonLabel.common.dto.user.PremiumRequestDto;
import org.ozonLabel.common.dto.user.UpdateOzonCredentialsDto;
import org.ozonLabel.common.dto.user.UpdateProfileDto;
import org.ozonLabel.common.dto.user.UserResponseDto;

import java.util.Optional;

public interface UserService {

    /**
     * Получение информации о текущем пользователе по email
     */
    UserResponseDto getCurrentUser(String email);

    /**
     * Обновление профиля пользователя
     * @param currentEmail текущий email пользователя
     * @param dto новые данные для обновления
     */
    UserResponseDto updateProfile(String currentEmail, UpdateProfileDto dto);

    /**
     * Обновление учетных данных Ozon
     * @param email email пользователя
     * @param dto новые данные Ozon
     */
    UserResponseDto updateOzonCredentials(String email, UpdateOzonCredentialsDto dto);

    /**
     * Запрос на премиум-подписку
     * @param requesterEmail email пользователя, который делает запрос
     * @param dto данные запроса премиум-подписки
     */
    void requestPremiumAccess(String requesterEmail, PremiumRequestDto dto);

    Optional<UserResponseDto> findByEmail(String email);
    boolean existsByEmail(String email);
    Optional<UserResponseDto> findByEmailCaseInsensitive(String email);
    Optional<UserResponseDto> findByPhone(String phone);
    /**
     * Найти пользователя по ID
     * @param id ID пользователя
     * @return Optional с данными пользователя
     */
    Optional<UserResponseDto> findById(Long id);
}
