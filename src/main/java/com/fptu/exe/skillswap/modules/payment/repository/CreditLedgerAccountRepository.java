package com.fptu.exe.skillswap.modules.payment.repository;

import com.fptu.exe.skillswap.modules.payment.domain.CreditLedgerAccount;
import com.fptu.exe.skillswap.modules.payment.domain.LedgerAccountType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CreditLedgerAccountRepository extends JpaRepository<CreditLedgerAccount, UUID> {

    Optional<CreditLedgerAccount> findByOwnerTypeAndOwnerId(LedgerAccountType ownerType, UUID ownerId);

    boolean existsByOwnerTypeAndOwnerId(LedgerAccountType ownerType, UUID ownerId);
}
