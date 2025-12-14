package org.ozonLabel.common.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

// LoginRequestDto.java
@Data
public class LoginRequestDto {
    @Email
    private String email;
    @NotBlank
    private String password;
}
