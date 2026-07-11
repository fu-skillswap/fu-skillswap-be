package com.fptu.exe.skillswap.infrastructure.security;

import java.util.UUID;

/**
 * Port interface in infrastructure layer for checking user ban status.
 * Implemented in modules.identity to avoid infrastructure -> modules dependency.
 */
public interface UserBanStatusPort {

    /**
     * Returns true if the user with the given ID is currently BANNED.
     * Returns true also if the user does not exist (treat as blocked).
     */
    boolean isBanned(UUID userId);
}
