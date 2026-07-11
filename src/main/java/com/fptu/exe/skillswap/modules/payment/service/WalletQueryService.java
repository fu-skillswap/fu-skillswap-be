package com.fptu.exe.skillswap.modules.payment.service;

import com.fptu.exe.skillswap.modules.payment.domain.CreditLedgerEntry;
import com.fptu.exe.skillswap.modules.payment.domain.SettlementEntry;
import com.fptu.exe.skillswap.modules.payment.dto.response.CreditWalletResponse;
import com.fptu.exe.skillswap.modules.payment.dto.response.MentorWalletResponse;
import com.fptu.exe.skillswap.modules.payment.dto.response.WalletTransactionResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WalletQueryService {

    private final CreditLedgerService creditLedgerService;
    private final SettlementService settlementService;

    @Transactional(readOnly = true)
    public CreditWalletResponse getMyCreditWallet(java.util.UUID userId) {
        return CreditWalletResponse.builder()
                .availableScoin(creditLedgerService.getAvailableBalance(userId))
                .recentTransactions(creditLedgerService.getRecentTransactions(userId).stream()
                        .map(WalletQueryService::toCreditTransactionResponse)
                        .toList())
                .build();
    }

    @Transactional(readOnly = true)
    public MentorWalletResponse getMyMentorWallet(java.util.UUID mentorUserId) {
        return MentorWalletResponse.builder()
                .availableScoin(settlementService.getMentorAvailableSettlement(mentorUserId))
                .recentTransactions(settlementService.getRecentTransactions(mentorUserId).stream()
                        .map(WalletQueryService::toSettlementTransactionResponse)
                        .toList())
                .build();
    }

    private static WalletTransactionResponse toCreditTransactionResponse(CreditLedgerEntry entry) {
        return WalletTransactionResponse.builder()
                .id(entry.getId())
                .entryType(entry.getEntryType())
                .originType(entry.getOriginType())
                .sourceType(entry.getSourceType())
                .sourceId(entry.getSourceId())
                .amountScoin(entry.getAmountScoin())
                .balanceEffectScoin(entry.getBalanceEffectScoin())
                .memo(entry.getMemo())
                .createdAt(entry.getCreatedAt())
                .build();
    }

    private static WalletTransactionResponse toSettlementTransactionResponse(SettlementEntry entry) {
        return WalletTransactionResponse.builder()
                .id(entry.getId())
                .entryType(entry.getEntryType() == null ? null : com.fptu.exe.skillswap.modules.payment.domain.LedgerEntryType.valueOf(entry.getEntryType().name()))
                .originType(null)
                .sourceType(entry.getSourceType())
                .sourceId(entry.getSourceId())
                .amountScoin(entry.getAmountScoin())
                .balanceEffectScoin(entry.getBalanceEffectScoin())
                .memo(entry.getMemo())
                .createdAt(entry.getCreatedAt())
                .build();
    }
}
