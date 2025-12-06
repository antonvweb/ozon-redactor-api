package org.ozonLabel.notification.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class NotificationPreferencesDto {
    private Boolean emailNotifications;
    private Boolean pushNotifications;
    private Boolean invitationNotifications;
    private Boolean systemNotifications;
    private Boolean productNotifications;
    private Boolean companyNotifications;
}