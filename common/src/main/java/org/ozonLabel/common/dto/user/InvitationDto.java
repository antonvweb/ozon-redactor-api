package org.ozonLabel.common.dto.user;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.ozonLabel.common.model.InvitationStatus;
import org.ozonLabel.common.model.MemberRole;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvitationDto {

    private Long id;
    private Long companyOwnerId;
    private String inviteeEmail;
    private String inviteePhone;
    private MemberRole role;
    private String token;
    private InvitationStatus status;
    private LocalDateTime expiresAt;
    private LocalDateTime acceptedAt;
    private Long acceptedByUserId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
