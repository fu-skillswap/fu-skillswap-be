package com.fptu.exe.skillswap.modules.booking.domain;

/**
 * Public booking-facing view of settlement state. Payment's internal
 * settlement enum must not leak through booking APIs.
 */
public enum BookingSettlementStatus {
    HELD,
    RELEASED,
    PARTIALLY_SETTLED,
    REFUNDED
}
