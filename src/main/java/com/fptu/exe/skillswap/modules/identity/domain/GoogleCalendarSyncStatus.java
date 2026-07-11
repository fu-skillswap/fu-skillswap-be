package com.fptu.exe.skillswap.modules.identity.domain;

public enum GoogleCalendarSyncStatus {
    NOT_CONNECTED,
    PENDING_SYNC,
    SYNCED,
    SYNC_ERROR,
    CANCELLED,
    REVOKED,
    EXPIRED_SYNC
}
