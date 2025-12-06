package org.ozonLabel.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationListResponseDto {
    private List<NotificationResponseDto> notifications;
    private Long unreadCount;
    private int currentPage;
    private int totalPages;
    private long totalElements;
}