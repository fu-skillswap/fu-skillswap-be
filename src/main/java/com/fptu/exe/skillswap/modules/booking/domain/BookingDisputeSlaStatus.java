package com.fptu.exe.skillswap.modules.booking.domain;

/** Public progress of a dispute; BookingStatus remains the persisted lifecycle source of truth. */
public enum BookingDisputeSlaStatus {
    WAITING_COUNTERPARTY,
    WAITING_ADMIN,
    ADMIN_SLA_OVERDUE,
    RESOLVED
}
