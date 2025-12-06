package org.ozonLabel.user.dto;

import lombok.Data;

// UpdateOzonCredentialsDto.java
@Data
public class UpdateOzonCredentialsDto {
    private String ozonClientId;
    private String ozonApiKey;

    public boolean isEmpty() {
        return ozonClientId == null && ozonApiKey == null;
    }
}
