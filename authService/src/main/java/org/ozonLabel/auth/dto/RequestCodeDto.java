package org.ozonLabel.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RequestCodeDto {
    
    @NotBlank(message = "Имя обязательно")
    private String name;
    
    @NotBlank(message = "Email обязателен")
    @Email(message = "Некорректный email")
    private String email;
    
    @NotBlank(message = "Пароль обязателен")
    @Size(min = 8, message = "Пароль должен быть минимум 8 символов")
    private String password;
    
    @NotBlank(message = "Подтверждение пароля обязательно")
    private String confirmPassword;
}
