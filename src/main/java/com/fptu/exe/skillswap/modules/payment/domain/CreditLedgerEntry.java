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
@Table(name = "credit_ledger_entries", indexes = {
        @Index(name = "idx_credit_entries_account", columnList = "account_id"),
        @Index(name = "idx_credit_entries_source", columnList = "source_type, source_id"),
        @Index(name = "idx_credit_entries_origin", columnList = "origin_type")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreditLedgerEntry {

    @Id
    @GeneratedUuidV7
    private UUID id;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 30)
    private LedgerEntryType entryType;

    @Enumerated(EnumType.STRING)
    @Column(name = "origin_type", nullable = false, length = 40)
    private CreditOriginType originType;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 40)
    private LedgerSourceType sourceType;

    @Column(name = "source_id")
    private UUID sourceId;

    @Column(name = "amount_scoin", nullable = false)
    private Integer amountScoin;

    @Column(name = "balance_effect_scoin", nullable = false)
    private Integer balanceEffectScoin;

    @Column(name = "memo", columnDefinition = "TEXT")
    private String memo;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @jakarta.persistence.PrePersist
    protected void onCreate() {
        createdAt = DateTimeUtil.now();
    }
}
