package org.ozonLabel.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateAccountDto {
    
    @NotBlank(message = "Email обязателен")
    @Email(message = "Некорректный email")
    private String email;
    
    @NotBlank(message = "Код подтверждения обязателен")
    private String code;
    
    private String companyName;
    private String inn;
    private String phone;
    private String ozonClientId;
    private String ozonApiKey;
}
