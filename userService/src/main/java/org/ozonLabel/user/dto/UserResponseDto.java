// src/main/java/org/ozonLabel/user/dto/UserResponseDto.java

package org.ozonLabel.user.dto;

import lombok.Data;

@Data
public class UserResponseDto {
    private Long id;
    private String name;
    private String email;
    private String companyName;
    private String inn;
    private String phone;

    // Теперь boolean — есть ли значение или нет
    private boolean hasOzonClientId;
    private boolean hasOzonApiKey;

    private String subscription;
}