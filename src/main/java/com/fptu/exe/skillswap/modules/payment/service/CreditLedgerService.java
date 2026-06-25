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
import com.fptu.exe.skillswap.shared.util.DateTimeUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
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

    @Transactional
    public CreditLedgerAccount ensureUserAccount(UUID userId) {
        if (userId == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "userId không được để trống");
        }
        return accountRepository.findByOwnerTypeAndOwnerId(LedgerAccountType.USER_CREDIT, userId)
                .orElseGet(() -> accountRepository.save(CreditLedgerAccount.builder()
                        .ownerType(LedgerAccountType.USER_CREDIT)
                        .ownerId(userId)
                        .accountCode("CREDIT_USER_" + userId)
                        .build()));
    }

    @Transactional
    public CreditLedgerAccount ensurePlatformAccount() {
        return accountRepository.findByOwnerTypeAndOwnerId(LedgerAccountType.PLATFORM_SETTLEMENT, PLATFORM_OWNER_ID)
                .orElseGet(() -> accountRepository.save(CreditLedgerAccount.builder()
                        .ownerType(LedgerAccountType.PLATFORM_SETTLEMENT)
                        .ownerId(PLATFORM_OWNER_ID)
                        .accountCode("CREDIT_PLATFORM")
                        .build()));
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
        CreditLedgerAccount account = ensureUserAccount(userId);
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
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Số credit hiện có không đủ để áp dụng cho checkout");
        }
        return entryRepository.findByAccountIdAndSourceTypeAndSourceIdAndEntryType(
                account.getId(), sourceType, sourceId, LedgerEntryType.RESERVE
        );
    }

    @Transactional
    public void releaseReservedCredit(UUID userId, LedgerSourceType sourceType, UUID sourceId, String memo) {
        CreditLedgerAccount account = ensureUserAccount(userId);
        if (entryRepository.findFirstByAccountIdAndSourceTypeAndSourceIdAndEntryTypeOrderByCreatedAtDesc(
                account.getId(), sourceType, sourceId, LedgerEntryType.RELEASE
        ).isPresent()) {
            return;
        }
        List<CreditLedgerEntry> reserves = entryRepository.findByAccountIdAndSourceTypeAndSourceIdAndEntryType(
                account.getId(), sourceType, sourceId, LedgerEntryType.RESERVE
        );
        for (CreditLedgerEntry reserve : reserves) {
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
        CreditLedgerAccount account = ensureUserAccount(userId);
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

    @Transactional
    public void refundCredit(UUID userId,
                             CreditOriginType originType,
                             LedgerSourceType sourceType,
                             UUID sourceId,
                             int amountScoin,
                             String memo) {
        if (amountScoin <= 0) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Số credit hoàn trả phải lớn hơn 0");
        }
        CreditLedgerAccount account = ensureUserAccount(userId);
        entryRepository.save(CreditLedgerEntry.builder()
                .accountId(account.getId())
                .entryType(LedgerEntryType.REFUND)
                .originType(originType)
                .sourceType(sourceType)
                .sourceId(sourceId)
                .amountScoin(amountScoin)
                .balanceEffectScoin(amountScoin)
                .memo(memo)
                .build());
    }

    private void reserve(UUID accountId,
                         CreditOriginType originType,
                         LedgerSourceType sourceType,
                         UUID sourceId,
                         int amountScoin,
                         String memo) {
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
