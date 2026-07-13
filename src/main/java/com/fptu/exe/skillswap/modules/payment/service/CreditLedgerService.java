package com.fptu.exe.skillswap.modules.payment.service;

import com.fptu.exe.skillswap.modules.payment.domain.CreditLedgerAccount;
import com.fptu.exe.skillswap.modules.payment.domain.CreditLedgerEntry;
import com.fptu.exe.skillswap.modules.payment.domain.CreditOriginType;
import com.fptu.exe.skillswap.modules.payment.domain.LedgerAccountType;
import com.fptu.exe.skillswap.modules.payment.domain.LedgerEntryType;
import com.fptu.exe.skillswap.modules.payment.domain.LedgerSourceType;
import com.fptu.exe.skillswap.modules.payment.repository.CreditLedgerAccountRepository;
import com.fptu.exe.skillswap.modules.payment.repository.CreditLedgerEntryRepository;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CreditLedgerService {

    private static final UUID PLATFORM_OWNER_ID = new UUID(0L, 1L);

    private final CreditLedgerAccountRepository accountRepository;
    private final CreditLedgerEntryRepository entryRepository;

    @Retryable(value = ObjectOptimisticLockingFailureException.class, maxAttempts = 3, backoff = @Backoff(delay = 100))
    @Transactional
    public CreditLedgerAccount ensureUserAccount(UUID userId) {
        if (userId == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "userId không được để trống");
        }
        return ensureAccount(LedgerAccountType.USER_CREDIT, userId, "CREDIT_USER_" + userId);
    }

    @Retryable(value = ObjectOptimisticLockingFailureException.class, maxAttempts = 3, backoff = @Backoff(delay = 100))
    @Transactional
    public CreditLedgerAccount ensurePlatformAccount() {
        return ensureAccount(LedgerAccountType.PLATFORM_SETTLEMENT, PLATFORM_OWNER_ID, "CREDIT_PLATFORM");
    }

    @Retryable(value = ObjectOptimisticLockingFailureException.class, maxAttempts = 3, backoff = @Backoff(delay = 100))
    @Transactional
    public CreditLedgerAccount getUserAccountForUpdate(UUID userId) {
        if (userId == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "userId không được để trống");
        }
        return lockAccount(LedgerAccountType.USER_CREDIT, userId, "CREDIT_USER_" + userId);
    }

    @Transactional(readOnly = true)
    public boolean hasIssuedCreditForSource(LedgerSourceType sourceType, UUID sourceId) {
        return entryRepository.existsBySourceTypeAndSourceIdAndEntryType(sourceType, sourceId, LedgerEntryType.ISSUE);
    }

    @Transactional(readOnly = true)
    public int getAvailableBalance(UUID userId) {
        CreditLedgerAccount account = ensureUserAccount(userId);
        return entryRepository.sumBalanceEffectByAccountId(account.getId()).intValue();
    }

    @Transactional(readOnly = true)
    public Map<CreditOriginType, Integer> getAvailableBalanceByOrigin(UUID userId) {
        CreditLedgerAccount account = ensureUserAccount(userId);
        Map<CreditOriginType, Integer> balances = new EnumMap<>(CreditOriginType.class);
        for (CreditOriginType originType : CreditOriginType.values()) {
            balances.put(originType, entryRepository.sumBalanceEffectByAccountIdAndOriginType(account.getId(), originType).intValue());
        }
        return balances;
    }

    @Transactional(readOnly = true)
    public List<CreditLedgerEntry> getRecentTransactions(UUID userId) {
        CreditLedgerAccount account = ensureUserAccount(userId);
        return entryRepository.findTop15ByAccountIdOrderByCreatedAtDesc(account.getId());
    }

    @Retryable(value = ObjectOptimisticLockingFailureException.class, maxAttempts = 3, backoff = @Backoff(delay = 100))
    @Transactional
    public List<CreditLedgerEntry> reserveCredit(UUID userId,
                                                 int amountScoin,
                                                 LedgerSourceType sourceType,
                                                 UUID sourceId,
                                                 List<CreditOriginType> priorityOrigins,
                                                 String memo) {
        if (amountScoin <= 0) {
            return List.of();
        }
        CreditLedgerAccount account = getUserAccountForUpdate(userId);
        int remaining = amountScoin;
        for (CreditOriginType originType : priorityOrigins) {
            if (remaining <= 0) {
                break;
            }
            int available = entryRepository.sumBalanceEffectByAccountIdAndOriginType(account.getId(), originType).intValue();
            if (available <= 0) {
                continue;
            }
            int applied = Math.min(remaining, available);
            reserve(account.getId(), originType, sourceType, sourceId, applied, memo);
            remaining -= applied;
        }
        if (remaining > 0) {
            throw new BaseException(ErrorCode.INSUFFICIENT_BALANCE, "Số credit khả dụng không đủ để thanh toán đơn hàng này");
        }
        return entryRepository.findByAccountIdAndSourceTypeAndSourceIdAndEntryType(
                account.getId(), sourceType, sourceId, LedgerEntryType.RESERVE
        );
    }

    @Retryable(value = ObjectOptimisticLockingFailureException.class, maxAttempts = 3, backoff = @Backoff(delay = 100))
    @Transactional
    public void consumeReservedCredit(UUID userId, LedgerSourceType sourceType, UUID sourceId, String memo) {
        CreditLedgerAccount account = getUserAccountForUpdate(userId);
        if (entryRepository.findFirstByAccountIdAndSourceTypeAndSourceIdAndEntryTypeOrderByCreatedAtDesc(
                account.getId(), sourceType, sourceId, LedgerEntryType.CONSUME
        ).isPresent()) {
            return;
        }
        List<CreditLedgerEntry> reserves = entryRepository.findByAccountIdAndSourceTypeAndSourceIdAndEntryType(
                account.getId(), sourceType, sourceId, LedgerEntryType.RESERVE
        );
        for (CreditLedgerEntry reserve : reserves) {
            entryRepository.save(CreditLedgerEntry.builder()
                    .accountId(account.getId())
                    .entryType(LedgerEntryType.CONSUME)
                    .originType(reserve.getOriginType())
                    .sourceType(sourceType)
                    .sourceId(sourceId)
                    .amountScoin(reserve.getAmountScoin())
                    .balanceEffectScoin(0)
                    .memo(memo)
                    .build());
        }
    }

    @Retryable(value = ObjectOptimisticLockingFailureException.class, maxAttempts = 3, backoff = @Backoff(delay = 100))
    @Transactional
    public void releaseReservedCredit(UUID userId, LedgerSourceType sourceType, UUID sourceId, String memo) {
        CreditLedgerAccount account = getUserAccountForUpdate(userId);
        if (entryRepository.findFirstByAccountIdAndSourceTypeAndSourceIdAndEntryTypeOrderByCreatedAtDesc(
                account.getId(), sourceType, sourceId, LedgerEntryType.RELEASE
        ).isPresent()) {
            return;
        }
        List<CreditLedgerEntry> reserves = entryRepository.findByAccountIdAndSourceTypeAndSourceIdAndEntryType(
                account.getId(), sourceType, sourceId, LedgerEntryType.RESERVE
        );
        for (CreditLedgerEntry reserve : reserves) {
            accountRepository.addBalance(account.getId(), reserve.getAmountScoin());
            entryRepository.save(CreditLedgerEntry.builder()
                    .accountId(account.getId())
                    .entryType(LedgerEntryType.RELEASE)
                    .originType(reserve.getOriginType())
                    .sourceType(sourceType)
                    .sourceId(sourceId)
                    .amountScoin(reserve.getAmountScoin())
                    .balanceEffectScoin(reserve.getAmountScoin())
                    .memo(memo)
                    .build());
        }
    }

    @Retryable(value = ObjectOptimisticLockingFailureException.class, maxAttempts = 3, backoff = @Backoff(delay = 100))
    @Transactional
    public CreditLedgerEntry issueCredit(UUID userId,
                                         CreditOriginType originType,
                                         LedgerSourceType sourceType,
                                         UUID sourceId,
                                         int amountScoin,
                                         String memo) {
        if (amountScoin <= 0) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Số credit phát hành phải lớn hơn 0");
        }
        CreditLedgerAccount account = getUserAccountForUpdate(userId);
        accountRepository.addBalance(account.getId(), amountScoin);
        return entryRepository.save(CreditLedgerEntry.builder()
                .accountId(account.getId())
                .entryType(LedgerEntryType.ISSUE)
                .originType(originType)
                .sourceType(sourceType)
                .sourceId(sourceId)
                .amountScoin(amountScoin)
                .balanceEffectScoin(amountScoin)
                .memo(memo)
                .build());
    }

    @Retryable(value = ObjectOptimisticLockingFailureException.class, maxAttempts = 3, backoff = @Backoff(delay = 100))
    @Transactional
    public void refundCredit(UUID userId,
                             CreditOriginType originType,
                             LedgerSourceType sourceType,
                             UUID sourceId,
                             int amountScoin,
                             String memo) {
        refundCredit(userId, originType, sourceType, sourceId, amountScoin, memo, null);
    }

    @Retryable(value = ObjectOptimisticLockingFailureException.class, maxAttempts = 3, backoff = @Backoff(delay = 100))
    @Transactional
    public void refundCredit(UUID userId,
                             CreditOriginType originType,
                             LedgerSourceType sourceType,
                             UUID sourceId,
                             int amountScoin,
                             String memo,
                             String operationKey) {
        if (amountScoin <= 0) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Số credit hoàn trả phải lớn hơn 0");
        }
        if (operationKey != null && entryRepository.findByOperationKey(operationKey).isPresent()) {
            return;
        }
        CreditLedgerAccount account = getUserAccountForUpdate(userId);
        if (operationKey != null && entryRepository.findByOperationKey(operationKey).isPresent()) {
            return;
        }
        accountRepository.addBalance(account.getId(), amountScoin);
        try {
            entryRepository.save(CreditLedgerEntry.builder()
                    .accountId(account.getId())
                    .entryType(LedgerEntryType.REFUND)
                    .originType(originType)
                    .sourceType(sourceType)
                    .sourceId(sourceId)
                    .amountScoin(amountScoin)
                    .balanceEffectScoin(amountScoin)
                    .memo(memo)
                    .operationKey(operationKey)
                    .build());
        } catch (DataIntegrityViolationException duplicate) {
            // The unique operation key is the final idempotency guard. The enclosing transaction rolls back balance addition.
            if (operationKey == null || entryRepository.findByOperationKey(operationKey).isEmpty()) {
                throw duplicate;
            }
        }
    }

    private CreditLedgerAccount ensureAccount(LedgerAccountType ownerType, UUID ownerId, String accountCode) {
        return accountRepository.findByOwnerTypeAndOwnerId(ownerType, ownerId)
                .orElseGet(() -> createAccount(ownerType, ownerId, accountCode));
    }

    private CreditLedgerAccount lockAccount(LedgerAccountType ownerType, UUID ownerId, String accountCode) {
        CreditLedgerAccount existing = accountRepository.findByOwnerTypeAndOwnerId(ownerType, ownerId)
                .orElse(null);
        if (existing != null) {
            return existing;
        }
        ensureAccount(ownerType, ownerId, accountCode);
        return accountRepository.findByOwnerTypeAndOwnerId(ownerType, ownerId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không thể khóa credit account"));
    }

    private CreditLedgerAccount createAccount(LedgerAccountType ownerType, UUID ownerId, String accountCode) {
        try {
            return accountRepository.save(CreditLedgerAccount.builder()
                    .ownerType(ownerType)
                    .ownerId(ownerId)
                    .accountCode(accountCode)
                    .build());
        } catch (DataIntegrityViolationException ex) {
            return accountRepository.findByOwnerTypeAndOwnerId(ownerType, ownerId)
                    .orElseThrow(() -> ex);
        }
    }

    private void reserve(UUID accountId,
                         CreditOriginType originType,
                         LedgerSourceType sourceType,
                         UUID sourceId,
                         int amountScoin,
                         String memo) {
        int rows = accountRepository.deductBalanceSafely(accountId, amountScoin);
        if (rows == 0) {
            throw new BaseException(ErrorCode.INSUFFICIENT_BALANCE, "Tài khoản không đủ số dư SCoin");
        }
        entryRepository.save(CreditLedgerEntry.builder()
                .accountId(accountId)
                .entryType(LedgerEntryType.RESERVE)
                .originType(originType)
                .sourceType(sourceType)
                .sourceId(sourceId)
                .amountScoin(amountScoin)
                .balanceEffectScoin(-amountScoin)
                .memo(memo)
                .build());
    }
}
