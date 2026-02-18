package org.ozonLabel.common.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RequestCodeDto {

    @NotBlank(message = "Имя обязательно")
    @Size(max = 100, message = "Имя не должно превышать 100 символов")
    private String name;

    @NotBlank(message = "Email обязателен")
    @Email(message = "Некорректный email")
    @Size(max = 255, message = "Email не должен превышать 255 символов")
    private String email;

    @NotBlank(message = "Пароль обязателен")
    @Size(min = 8, max = 128, message = "Пароль должен быть от 8 до 128 символов")
    @Pattern(
            regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]+$",
            message = "Пароль должен содержать минимум одну заглавную букву, одну строчную букву, одну цифру и один специальный символ (@$!%*?&)"
    )
    private String password;

    @NotBlank(message = "Подтверждение пароля обязательно")
    private String confirmPassword;
}
