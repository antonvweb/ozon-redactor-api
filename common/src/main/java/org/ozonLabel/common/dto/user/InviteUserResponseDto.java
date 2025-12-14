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
public class InviteUserResponseDto {

    private Long invitationId;
    private String token;
    private String invitationLink;
    private String inviteeEmail;
    private String inviteePhone;
    private String role;                      // "ADMIN", "MODERATOR", "VIEWER"
    private LocalDateTime expiresAt;
    private String message;

    // Опционально: можно добавить имя/компанию отправителя для фронта
    private String invitedByName;
    private String invitedByCompanyName;
}
