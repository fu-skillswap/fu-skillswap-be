package com.fptu.exe.skillswap.modules.identity.event;

import com.fptu.exe.skillswap.modules.identity.domain.UserStatus;

import java.util.UUID;

public record UserStatusChangedEvent(
        UUID userId,
        UserStatus oldStatus,
        UserStatus newStatus
) {
}
