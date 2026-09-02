package com.fptu.exe.skillswap.modules.booking.domain;

/** Calendar synchronization state owned by the session aggregate. */
public enum CalendarSyncStatus {
    PENDING_SYNC,
    SYNCED,
    SYNC_ERROR,
    NOT_CONNECTED,
    CANCELLED,
    REVOKED,
    EXPIRED_SYNC
}
