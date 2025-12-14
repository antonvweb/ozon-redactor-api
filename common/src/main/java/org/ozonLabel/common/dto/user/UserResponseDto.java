// src/main/java/org/ozonLabel/user/dto/UserResponseDto.java

package org.ozonLabel.common.dto.user;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponseDto {
    private Long id;
    private String name;
    private String email;
    private String companyName;
    private String inn;
    private String phone;
    private String subscription;
    private String ozonClientId;
    private String ozonApiKey;
    private Boolean hasOzonClientId;
    private Boolean hasOzonApiKey;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String refreshToken;
    private LocalDateTime refreshTokenExpiresAt;
    private LocalDateTime accountLockedUntil;
    private Boolean emailVerified;
    private LocalDateTime lastLoginAt;
    private Integer loginAttempts;
    private LocalDateTime passwordChangedAt;
}