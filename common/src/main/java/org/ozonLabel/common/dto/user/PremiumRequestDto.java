package org.ozonLabel.common.dto.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

// PremiumRequestDto.java
@Data
public class PremiumRequestDto {
    @Email(message = "Некорректный email")
    @NotBlank
    private String email;
}
