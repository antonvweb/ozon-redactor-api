package org.ozonLabel.user.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class BulkNotificationActionDto {

    @NotNull(message = "Notification IDs list cannot be null")
    @Size(min = 1, max = 100, message = "You can process between 1 and 100 notifications at once")
    private List<Long> notificationIds;
}