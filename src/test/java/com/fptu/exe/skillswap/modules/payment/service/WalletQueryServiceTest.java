package com.fptu.exe.skillswap.modules.payment.service;

import com.fptu.exe.skillswap.modules.payment.domain.CreditLedgerEntry;
import com.fptu.exe.skillswap.modules.payment.domain.CreditOriginType;
import com.fptu.exe.skillswap.modules.payment.domain.LedgerEntryType;
import com.fptu.exe.skillswap.modules.payment.domain.LedgerSourceType;
import com.fptu.exe.skillswap.modules.payment.domain.SettlementEntry;
import com.fptu.exe.skillswap.modules.payment.domain.SettlementEntryType;
import com.fptu.exe.skillswap.modules.payment.dto.response.CreditWalletResponse;
import com.fptu.exe.skillswap.modules.payment.dto.response.MentorWalletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WalletQueryServiceTest {

    @Mock
    private CreditLedgerService creditLedgerService;

    @Mock
    private SettlementService settlementService;

    @InjectMocks
    private WalletQueryService walletQueryService;

    @Test
    void getMyCreditWallet_shouldMapAvailableBalanceAndRecentTransactions() {
        UUID userId = UUID.randomUUID();
        CreditLedgerEntry entry = CreditLedgerEntry.builder()
                .id(UUID.randomUUID())
                .entryType(LedgerEntryType.REFUND)
                .originType(CreditOriginType.REFUND)
                .sourceType(LedgerSourceType.REFUND)
                .sourceId(UUID.randomUUID())
                .amountScoin(120)
                .balanceEffectScoin(120)
                .memo("Refund from booking")
                .createdAt(LocalDateTime.now())
                .build();

        when(creditLedgerService.getAvailableBalance(eq(userId))).thenReturn(820);
        when(creditLedgerService.getRecentTransactions(eq(userId))).thenReturn(List.of(entry));

        CreditWalletResponse response = walletQueryService.getMyCreditWallet(userId);

        assertThat(response.availableScoin()).isEqualTo(820);
        assertThat(response.recentTransactions()).hasSize(1);
        assertThat(response.recentTransactions().get(0).entryType()).isEqualTo(LedgerEntryType.REFUND);
        assertThat(response.recentTransactions().get(0).originType()).isEqualTo(CreditOriginType.REFUND);
    }

    @Test
    void getMyMentorWallet_shouldMapAvailableBalanceAndRecentTransactions() {
        UUID mentorUserId = UUID.randomUUID();
        SettlementEntry entry = SettlementEntry.builder()
                .id(UUID.randomUUID())
                .entryType(SettlementEntryType.RELEASE)
                .sourceType(LedgerSourceType.BOOKING)
                .sourceId(UUID.randomUUID())
                .amountScoin(500)
                .balanceEffectScoin(500)
                .memo("Release for completed booking")
                .createdAt(LocalDateTime.now())
                .build();

        when(settlementService.getMentorAvailableSettlement(eq(mentorUserId))).thenReturn(5600);
        when(settlementService.getRecentTransactions(eq(mentorUserId))).thenReturn(List.of(entry));

        MentorWalletResponse response = walletQueryService.getMyMentorWallet(mentorUserId);

        assertThat(response.availableScoin()).isEqualTo(5600);
        assertThat(response.recentTransactions()).hasSize(1);
        assertThat(response.recentTransactions().get(0).entryType()).isEqualTo(LedgerEntryType.RELEASE);
        assertThat(response.recentTransactions().get(0).originType()).isNull();
    }
}
