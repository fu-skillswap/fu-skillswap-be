package com.fptu.exe.skillswap.modules.payment.repository;

import com.fptu.exe.skillswap.modules.payment.domain.LedgerAccountType;
import com.fptu.exe.skillswap.modules.payment.domain.SettlementAccount;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface SettlementAccountRepository extends JpaRepository<SettlementAccount, UUID> {

    Optional<SettlementAccount> findByOwnerTypeAndOwnerId(LedgerAccountType ownerType, UUID ownerId);

    @Query("""
            select account
            from SettlementAccount account
            where account.ownerType = :ownerType
              and account.ownerId = :ownerId
            """)
    Optional<SettlementAccount> findByOwnerTypeAndOwnerIdForUpdate(@Param("ownerType") LedgerAccountType ownerType,
                                                                   @Param("ownerId") UUID ownerId);

    boolean existsByOwnerTypeAndOwnerId(LedgerAccountType ownerType, UUID ownerId);

    @org.springframework.data.jpa.repository.Modifying
    @Query("""
            UPDATE SettlementAccount a
            SET a.balance = a.balance - :amount
            WHERE a.id = :id AND a.balance >= :amount
            """)
    int deductBalanceSafely(@Param("id") UUID id, @Param("amount") java.math.BigDecimal amount);

    @org.springframework.data.jpa.repository.Modifying
    @Query("""
            UPDATE SettlementAccount a
            SET a.balance = a.balance + :amount
            WHERE a.id = :id
            """)
    int addBalance(@Param("id") UUID id, @Param("amount") java.math.BigDecimal amount);
}
