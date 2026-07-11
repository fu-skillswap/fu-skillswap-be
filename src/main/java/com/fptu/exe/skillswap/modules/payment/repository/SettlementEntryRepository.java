package com.fptu.exe.skillswap.modules.payment.repository;

import com.fptu.exe.skillswap.modules.payment.domain.LedgerSourceType;
import com.fptu.exe.skillswap.modules.payment.domain.SettlementEntry;
import com.fptu.exe.skillswap.modules.payment.domain.SettlementEntryType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SettlementEntryRepository extends JpaRepository<SettlementEntry, UUID> {

    @Query("""
            select coalesce(sum(e.balanceEffectScoin), 0)
            from SettlementEntry e
            where e.accountId = :accountId
            """)
    Long sumBalanceEffectByAccountId(UUID accountId);

    List<SettlementEntry> findByAccountIdAndSourceTypeAndSourceIdAndEntryType(UUID accountId,
                                                                              LedgerSourceType sourceType,
                                                                              UUID sourceId,
                                                                              SettlementEntryType entryType);

    Optional<SettlementEntry> findFirstByAccountIdAndSourceTypeAndSourceIdAndEntryTypeOrderByCreatedAtDesc(
            UUID accountId,
            LedgerSourceType sourceType,
            UUID sourceId,
            SettlementEntryType entryType
    );

    List<SettlementEntry> findTop15ByAccountIdOrderByCreatedAtDesc(UUID accountId);
}
