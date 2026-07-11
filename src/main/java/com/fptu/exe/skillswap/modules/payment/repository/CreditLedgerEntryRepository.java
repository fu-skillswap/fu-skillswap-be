package com.fptu.exe.skillswap.modules.payment.repository;

import com.fptu.exe.skillswap.modules.payment.domain.CreditLedgerEntry;
import com.fptu.exe.skillswap.modules.payment.domain.CreditOriginType;
import com.fptu.exe.skillswap.modules.payment.domain.LedgerEntryType;
import com.fptu.exe.skillswap.modules.payment.domain.LedgerSourceType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CreditLedgerEntryRepository extends JpaRepository<CreditLedgerEntry, UUID> {

    @Query("""
            select coalesce(sum(e.balanceEffectScoin), 0)
            from CreditLedgerEntry e
            where e.accountId = :accountId
            """)
    Long sumBalanceEffectByAccountId(UUID accountId);

    @Query("""
            select coalesce(sum(e.balanceEffectScoin), 0)
            from CreditLedgerEntry e
            where e.accountId = :accountId
              and e.originType = :originType
            """)
    Long sumBalanceEffectByAccountIdAndOriginType(UUID accountId, CreditOriginType originType);

    List<CreditLedgerEntry> findByAccountIdAndSourceTypeAndSourceIdAndEntryType(UUID accountId,
                                                                               LedgerSourceType sourceType,
                                                                               UUID sourceId,
                                                                               LedgerEntryType entryType);

    Optional<CreditLedgerEntry> findFirstByAccountIdAndSourceTypeAndSourceIdAndEntryTypeOrderByCreatedAtDesc(
            UUID accountId,
            LedgerSourceType sourceType,
            UUID sourceId,
            LedgerEntryType entryType
    );

    List<CreditLedgerEntry> findTop15ByAccountIdOrderByCreatedAtDesc(UUID accountId);
}
