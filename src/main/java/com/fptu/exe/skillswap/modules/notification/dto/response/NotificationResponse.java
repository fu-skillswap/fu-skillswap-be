package com.fptu.exe.skillswap.modules.notification.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationResponse {
    private UUID notificationId;
    private String type;
    private String title;
    private String message;
    private String relatedEntityType;
    private UUID relatedEntityId;
    private boolean read;
    private LocalDateTime readAt;
    private LocalDateTime createdAt;
}
