package org.ozonLabel.common.dto.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotNull;
import org.ozonLabel.common.model.MemberRole;

/**
 * DTO для запроса на приглашение пользователя в компанию
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InviteUserRequestDto {

    @Email(message = "Некорректный формат email")
    private String email;

    // Российский формат телефона: +7XXXXXXXXXX или 8XXXXXXXXXX или просто 10 цифр
    @Pattern(
            regexp = "^(\\+7|8|7)?[0-9]{10}$|^$",
            message = "Некорректный формат телефона"
    )
    private String phone;


    @NotNull(message = "Роль обязательна")
    private MemberRole role;

    /**
     * Удобный метод для проверки, что хотя бы один контакт указан
     */
    public boolean isEmpty() {
        return (email == null || email.trim().isEmpty())
                && (phone == null || phone.trim().isEmpty());
    }

    /**
     * Нормализация телефона: приводим к виду +79991234567
     */
    public String getNormalizedPhone() {
        if (phone == null || phone.trim().isEmpty()) {
            return null;
        }
        String digits = phone.replaceAll("[^0-9]", "");
        if (digits.startsWith("8") && digits.length() == 11) {
            digits = digits.substring(1);
        }
        if (digits.startsWith("7") && digits.length() == 11) {
            digits = digits.substring(1);
        }
        if (digits.length() == 10) {
            return "+7" + digits;
        }
        return phone; // fallback
    }
}
