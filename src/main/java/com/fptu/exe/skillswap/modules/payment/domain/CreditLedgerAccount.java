package com.fptu.exe.skillswap.modules.payment.domain;

import com.fptu.exe.skillswap.shared.persistence.GeneratedUuidV7;
import com.fptu.exe.skillswap.shared.util.DateTimeUtil;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "credit_ledger_accounts", indexes = {
        @Index(name = "idx_credit_accounts_owner", columnList = "owner_type, owner_id", unique = true),
        @Index(name = "idx_credit_accounts_code", columnList = "account_code", unique = true)
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreditLedgerAccount {

    @Id
    @GeneratedUuidV7
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "owner_type", nullable = false, length = 40)
    private LedgerAccountType ownerType;

    @Column(name = "owner_id")
    private UUID ownerId;

    @Column(name = "account_code", nullable = false, unique = true, length = 120)
    private String accountCode;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(nullable = false)
    @Builder.Default
    private int balance = 0;

    @jakarta.persistence.Version
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "created_at_utc", nullable = false, updatable = false)
    private java.time.Instant createdAtUtc;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "updated_at_utc", nullable = false)
    private java.time.Instant updatedAtUtc;

    @jakarta.persistence.PrePersist
    protected void onCreate() {
        java.time.Instant nowUtc = com.fptu.exe.skillswap.shared.util.DateTimeUtil.instantNow();
        LocalDateTime nowHcm = com.fptu.exe.skillswap.modules.booking.service.BookingTime.fromInstant(nowUtc);
        if (createdAt == null && createdAtUtc == null) {
            createdAtUtc = nowUtc;
            createdAt = nowHcm;
        } else if (createdAtUtc != null && createdAt == null) {
            createdAt = com.fptu.exe.skillswap.modules.booking.service.BookingTime.fromInstant(createdAtUtc);
        } else if (createdAt != null && createdAtUtc == null) {
            createdAtUtc = com.fptu.exe.skillswap.modules.booking.service.BookingTime.toInstant(createdAt);
        }
        updatedAtUtc = nowUtc;
        updatedAt = nowHcm;
    }

    @jakarta.persistence.PreUpdate
    protected void onUpdate() {
        updatedAtUtc = com.fptu.exe.skillswap.shared.util.DateTimeUtil.instantNow();
        updatedAt = com.fptu.exe.skillswap.modules.booking.service.BookingTime.fromInstant(updatedAtUtc);
    }
}
