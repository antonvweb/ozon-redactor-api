package org.ozonLabel.notification.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationStatsDto {
    private Long totalUnread;
    private Long totalInvitations;
    private Long totalSystem;
    private Long totalSupport;
    private Long urgentCount;
    private LocalDateTime lastChecked;
}
