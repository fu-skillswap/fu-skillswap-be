package com.fptu.exe.skillswap.modules.notification.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Unread notification counter for the current user. Use this to render notification badges without loading the full notification list.")
public class UnreadCountResponse {
    @Schema(description = "Number of unread notifications", example = "3")
    private long unreadCount;
}
