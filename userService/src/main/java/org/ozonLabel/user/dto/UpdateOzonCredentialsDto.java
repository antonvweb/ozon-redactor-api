// src/main/java/org/ozonLabel/user/dto/UpdateOzonCredentialsDto.java

package org.ozonLabel.user.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class UpdateOzonCredentialsDto {
    private String ozonClientId;
    private String ozonApiKey;

    public boolean isEmpty() {
        return (ozonClientId == null || ozonClientId.trim().isEmpty())
                && (ozonApiKey == null || ozonApiKey.trim().isEmpty());
    }
}