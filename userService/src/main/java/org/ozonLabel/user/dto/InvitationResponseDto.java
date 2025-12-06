package org.ozonLabel.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class InvitationResponseDto {
    private Long notificationId;
    private Long invitationId;
    private String action; // "accept" или "reject"
}