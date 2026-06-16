package com.fptu.exe.skillswap.shared.event;

import java.util.UUID;

public class UserBannedEvent {
    private final UUID userId;

    public UserBannedEvent(UUID userId) {
        this.userId = userId;
    }

    public UUID getUserId() {
        return userId;
    }
}
