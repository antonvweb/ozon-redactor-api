package org.ozonLabel.common.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class LoginRequestDto {

    @NotBlank(message = "Email обязателен")
    @Email(message = "Некорректный email")
    @Size(max = 255, message = "Email не должен превышать 255 символов")
    private String email;

    @NotBlank(message = "Пароль обязателен")
    @Size(max = 128, message = "Пароль не должен превышать 128 символов")
    private String password;
}
