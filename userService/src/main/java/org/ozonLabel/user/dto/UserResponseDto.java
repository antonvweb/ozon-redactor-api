package org.ozonLabel.user.dto;

import lombok.Data;

// UserResponseDto.java
@Data
public class UserResponseDto {
    private Long id;
    private String name;
    private String email;
    private String companyName;
    private String inn;
    private String phone;
    private String ozonClientId;
    private String subscription;
}
