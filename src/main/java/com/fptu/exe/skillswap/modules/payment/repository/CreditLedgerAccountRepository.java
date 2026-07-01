package com.fptu.exe.skillswap.modules.payment.repository;

import com.fptu.exe.skillswap.modules.payment.domain.CreditLedgerAccount;
import com.fptu.exe.skillswap.modules.payment.domain.LedgerAccountType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface CreditLedgerAccountRepository extends JpaRepository<CreditLedgerAccount, UUID> {

    Optional<CreditLedgerAccount> findByOwnerTypeAndOwnerId(LedgerAccountType ownerType, UUID ownerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select account
            from CreditLedgerAccount account
            where account.ownerType = :ownerType
              and account.ownerId = :ownerId
            """)
    Optional<CreditLedgerAccount> findByOwnerTypeAndOwnerIdForUpdate(@Param("ownerType") LedgerAccountType ownerType,
                                                                     @Param("ownerId") UUID ownerId);

    boolean existsByOwnerTypeAndOwnerId(LedgerAccountType ownerType, UUID ownerId);
}
