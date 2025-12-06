package org.ozonLabel.user.dto;

import lombok.Data;

// UpdateProfileDto.java
@Data
public class UpdateProfileDto {
    private String companyName;
    private String inn;
    private String phone;
    private String email;

    public boolean isEmpty() {
        return companyName == null && inn == null && phone == null && email == null;
    }
}