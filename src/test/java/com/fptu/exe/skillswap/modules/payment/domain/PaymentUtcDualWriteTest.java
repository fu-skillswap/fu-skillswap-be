package com.fptu.exe.skillswap.modules.payment.domain;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PaymentUtcDualWriteTest {

    private TimeZone defaultTimeZone;

    @BeforeEach
    void setUp() {
        defaultTimeZone = TimeZone.getDefault();
    }

    @AfterEach
    void tearDown() {
        TimeZone.setDefault(defaultTimeZone);
    }

    @Test
    void paymentOrder_onCreate_synchronizesLegacyToUtcShadow() {
        LocalDateTime hcmExpires = LocalDateTime.of(2026, 8, 24, 15, 30, 0); // 15:30 HCM = 08:30 UTC
        LocalDateTime hcmPaid = LocalDateTime.of(2026, 8, 24, 15, 20, 0);    // 15:20 HCM = 08:20 UTC

        PaymentOrder order = PaymentOrder.builder()
                .expiresAt(hcmExpires)
                .paidAt(hcmPaid)
                .build();

        order.onCreate();

        assertNotNull(order.getCreatedAt());
        assertNotNull(order.getCreatedAtUtc());
        assertEquals(Instant.parse("2026-08-24T08:30:00Z"), order.getExpiresAtUtc());
        assertEquals(Instant.parse("2026-08-24T08:20:00Z"), order.getPaidAtUtc());
    }

    @Test
    void paymentOrder_onCreate_synchronizesUtcToLegacy() {
        Instant utcExpires = Instant.parse("2026-08-24T10:00:00Z"); // 10:00 UTC = 17:00 HCM
        Instant utcPaid = Instant.parse("2026-08-24T09:45:00Z");    // 09:45 UTC = 16:45 HCM

        PaymentOrder order = PaymentOrder.builder()
                .expiresAtUtc(utcExpires)
                .paidAtUtc(utcPaid)
                .build();

        order.onCreate();

        assertEquals(LocalDateTime.of(2026, 8, 24, 17, 0, 0), order.getExpiresAt());
        assertEquals(LocalDateTime.of(2026, 8, 24, 16, 45, 0), order.getPaidAt());
    }

    @Test
    void paymentOrder_onUpdate_synchronizesAllShadowFields() {
        PaymentOrder order = PaymentOrder.builder().build();
        order.onCreate();

        order.setReleasedAtUtc(Instant.parse("2026-08-25T01:00:00Z")); // 01:00 UTC = 08:00 HCM
        order.setRefundedAt(LocalDateTime.of(2026, 8, 25, 10, 0, 0)); // 10:00 HCM = 03:00 UTC
        order.onUpdate();

        assertEquals(LocalDateTime.of(2026, 8, 25, 8, 0, 0), order.getReleasedAt());
        assertEquals(Instant.parse("2026-08-25T03:00:00Z"), order.getRefundedAtUtc());
    }

    @Test
    void paymentAttempt_onCreate_synchronizesBidirectional() {
        LocalDateTime hcmCreated = LocalDateTime.of(2026, 8, 24, 14, 0, 0); // 14:00 HCM = 07:00 UTC

        PaymentAttempt attempt = PaymentAttempt.builder()
                .createdAt(hcmCreated)
                .build();

        attempt.onCreate();

        assertNotNull(attempt.getCreatedAtUtc());
        assertEquals(Instant.parse("2026-08-24T07:00:00Z"), attempt.getCreatedAtUtc());
    }

    @Test
    void settlementEntry_and_creditLedgerEntry_onCreate_synchronizes() {
        SettlementEntry settlementEntry = SettlementEntry.builder().build();
        settlementEntry.onCreate();
        assertNotNull(settlementEntry.getCreatedAt());
        assertNotNull(settlementEntry.getCreatedAtUtc());

        CreditLedgerEntry creditEntry = CreditLedgerEntry.builder().build();
        creditEntry.onCreate();
        assertNotNull(creditEntry.getCreatedAt());
        assertNotNull(creditEntry.getCreatedAtUtc());
    }

    @Test
    void payoutRequest_onCreate_and_onUpdate_synchronizesAllTimestamps() {
        PayoutRequest request = PayoutRequest.builder()
                .requestedAt(LocalDateTime.of(2026, 8, 24, 12, 0, 0)) // 12:00 HCM = 05:00 UTC
                .build();

        request.onCreate();

        assertEquals(Instant.parse("2026-08-24T05:00:00Z"), request.getRequestedAtUtc());

        request.setApprovedAtUtc(Instant.parse("2026-08-24T06:00:00Z")); // 06:00 UTC = 13:00 HCM
        request.setPaidAt(LocalDateTime.of(2026, 8, 24, 14, 0, 0));     // 14:00 HCM = 07:00 UTC
        request.onUpdate();

        assertEquals(LocalDateTime.of(2026, 8, 24, 13, 0, 0), request.getApprovedAt());
        assertEquals(Instant.parse("2026-08-24T07:00:00Z"), request.getPaidAtUtc());
    }

    @Test
    void dualWrite_isIndependentOfJvmTimezone() {
        TimeZone[] testZones = {
                TimeZone.getTimeZone("UTC"),
                TimeZone.getTimeZone("Asia/Ho_Chi_Minh"),
                TimeZone.getTimeZone("America/New_York"),
                TimeZone.getTimeZone("Europe/London"),
                TimeZone.getTimeZone("Asia/Tokyo")
        };

        for (TimeZone zone : testZones) {
            TimeZone.setDefault(zone);

            PaymentOrder order = PaymentOrder.builder()
                    .expiresAtUtc(Instant.parse("2026-08-24T08:00:00Z")) // 08:00 UTC = 15:00 HCM
                    .build();

            order.onCreate();

            assertEquals(LocalDateTime.of(2026, 8, 24, 15, 0, 0), order.getExpiresAt(),
                    "Mismatch under JVM timezone: " + zone.getID());
            assertEquals(Instant.parse("2026-08-24T08:00:00Z"), order.getExpiresAtUtc(),
                    "Mismatch under JVM timezone: " + zone.getID());
        }
    }
}
