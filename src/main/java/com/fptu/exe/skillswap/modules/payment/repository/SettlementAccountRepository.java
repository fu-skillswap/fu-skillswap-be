package com.fptu.exe.skillswap.modules.payment.repository;

import com.fptu.exe.skillswap.modules.payment.domain.LedgerAccountType;
import com.fptu.exe.skillswap.modules.payment.domain.SettlementAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SettlementAccountRepository extends JpaRepository<SettlementAccount, UUID> {

    Optional<SettlementAccount> findByOwnerTypeAndOwnerId(LedgerAccountType ownerType, UUID ownerId);

    boolean existsByOwnerTypeAndOwnerId(LedgerAccountType ownerType, UUID ownerId);
}
