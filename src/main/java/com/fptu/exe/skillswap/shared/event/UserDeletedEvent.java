package com.fptu.exe.skillswap.shared.event;

import java.util.UUID;

public class UserDeletedEvent {
    private final UUID userId;

    public UserDeletedEvent(UUID userId) {
        this.userId = userId;
    }

    public UUID getUserId() {
        return userId;
    }
}
