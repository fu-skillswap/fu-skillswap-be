package com.fptu.exe.skillswap.modules.identity.domain;

public enum UserSessionState {
    ACTIVE,
    ROTATING_GRACE,
    REVOKED,
    EXPIRED
}
