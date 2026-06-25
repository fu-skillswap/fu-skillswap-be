package com.fptu.exe.skillswap.modules.payment.service;

import com.fptu.exe.skillswap.infrastructure.config.PaymentProperties;
import com.fptu.exe.skillswap.modules.booking.domain.Booking;
import com.fptu.exe.skillswap.modules.booking.domain.BookingCompletionOutcome;
import com.fptu.exe.skillswap.modules.booking.domain.BookingStatus;
import com.fptu.exe.skillswap.modules.payment.domain.LedgerAccountType;
import com.fptu.exe.skillswap.modules.payment.domain.LedgerSourceType;
import com.fptu.exe.skillswap.modules.payment.domain.SettlementAccount;
import com.fptu.exe.skillswap.modules.payment.domain.SettlementEntry;
import com.fptu.exe.skillswap.modules.payment.domain.SettlementEntryType;
import com.fptu.exe.skillswap.modules.payment.repository.SettlementAccountRepository;
import com.fptu.exe.skillswap.modules.payment.repository.SettlementEntryRepository;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SettlementService {

    private static final int COMMISSION_BPS_DEFAULT = 1000;
    private static final UUID PLATFORM_OWNER_ID = new UUID(0L, 1L);

    private final SettlementAccountRepository settlementAccountRepository;
    private final SettlementEntryRepository settlementEntryRepository;
    private final PaymentProperties paymentProperties;

    @Transactional
    public SettlementAccount ensureMentorAccount(UUID mentorUserId) {
        if (mentorUserId == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "mentorUserId không được để trống");
        }
        return settlementAccountRepository.findByOwnerTypeAndOwnerId(LedgerAccountType.MENTOR_SETTLEMENT, mentorUserId)
                .orElseGet(() -> settlementAccountRepository.save(SettlementAccount.builder()
                        .ownerType(LedgerAccountType.MENTOR_SETTLEMENT)
                        .ownerId(mentorUserId)
                        .accountCode("SETTLEMENT_MENTOR_" + mentorUserId)
                        .build()));
    }

    @Transactional
    public SettlementAccount ensurePlatformAccount() {
        return settlementAccountRepository.findByOwnerTypeAndOwnerId(LedgerAccountType.PLATFORM_SETTLEMENT, PLATFORM_OWNER_ID)
                .orElseGet(() -> settlementAccountRepository.save(SettlementAccount.builder()
                        .ownerType(LedgerAccountType.PLATFORM_SETTLEMENT)
                        .ownerId(PLATFORM_OWNER_ID)
                        .accountCode("SETTLEMENT_PLATFORM")
                        .build()));
    }

    @Transactional
    public void releaseForBooking(Booking booking) {
        if (booking == null || booking.getId() == null) {
            return;
        }
        if (booking.getMentorProfile() == null || booking.getMentorProfile().getUserId() == null) {
            return;
        }
        if (booking.getStatus() != BookingStatus.COMPLETED
                && booking.getStatus() != BookingStatus.AUTO_CLOSED) {
            return;
        }
        if (booking.getCompletionOutcome() == BookingCompletionOutcome.REVIEW_PENDING_DECISION) {
            return;
        }
        if (settlementEntryRepository.findFirstByAccountIdAndSourceTypeAndSourceIdAndEntryTypeOrderByCreatedAtDesc(
                ensureMentorAccount(booking.getMentorProfile().getUserId()).getId(),
                LedgerSourceType.BOOKING,
                booking.getId(),
                SettlementEntryType.RELEASE
        ).isPresent()) {
            return;
        }

        int grossScoin = Math.max(0, booking.getServicePriceScoinSnapshot() == null ? 0 : booking.getServicePriceScoinSnapshot());
        int commissionBps = paymentProperties == null ? COMMISSION_BPS_DEFAULT : paymentProperties.getPlatformCommissionBps();
        int commissionScoin = Math.max(0, (grossScoin * commissionBps) / 10_000);
        int releasableScoin = Math.max(0, grossScoin - commissionScoin);

        SettlementAccount mentorAccount = ensureMentorAccount(booking.getMentorProfile().getUserId());
        SettlementAccount platformAccount = ensurePlatformAccount();

        settlementEntryRepository.save(SettlementEntry.builder()
                .accountId(mentorAccount.getId())
                .entryType(SettlementEntryType.RELEASE)
                .sourceType(LedgerSourceType.BOOKING)
                .sourceId(booking.getId())
                .amountScoin(releasableScoin)
                .balanceEffectScoin(releasableScoin)
                .memo("Release for completed booking " + booking.getId())
                .build());

        settlementEntryRepository.save(SettlementEntry.builder()
                .accountId(platformAccount.getId())
                .entryType(SettlementEntryType.COMMISSION)
                .sourceType(LedgerSourceType.BOOKING)
                .sourceId(booking.getId())
                .amountScoin(commissionScoin)
                .balanceEffectScoin(commissionScoin)
                .commissionScoin(commissionScoin)
                .memo("Platform commission for booking " + booking.getId())
                .build());
    }

    @Transactional(readOnly = true)
    public int getMentorAvailableSettlement(UUID mentorUserId) {
        SettlementAccount account = ensureMentorAccount(mentorUserId);
        return settlementEntryRepository.sumBalanceEffectByAccountId(account.getId()).intValue();
    }

    @Transactional
    public SettlementEntry holdPayout(UUID mentorUserId, UUID payoutRequestId, int amountScoin, String memo) {
        if (amountScoin <= 0) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "amountScoin phải lớn hơn 0");
        }
        SettlementAccount account = ensureMentorAccount(mentorUserId);
        int available = settlementEntryRepository.sumBalanceEffectByAccountId(account.getId()).intValue();
        if (available < amountScoin) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Số dư settlement chưa đủ để tạo payout request");
        }
        if (settlementEntryRepository.findFirstByAccountIdAndSourceTypeAndSourceIdAndEntryTypeOrderByCreatedAtDesc(
                account.getId(), LedgerSourceType.PAYOUT_REQUEST, payoutRequestId, SettlementEntryType.HOLD
        ).isPresent()) {
            return settlementEntryRepository.findFirstByAccountIdAndSourceTypeAndSourceIdAndEntryTypeOrderByCreatedAtDesc(
                    account.getId(), LedgerSourceType.PAYOUT_REQUEST, payoutRequestId, SettlementEntryType.HOLD
            ).orElseThrow();
        }
        return settlementEntryRepository.save(SettlementEntry.builder()
                .accountId(account.getId())
                .entryType(SettlementEntryType.HOLD)
                .sourceType(LedgerSourceType.PAYOUT_REQUEST)
                .sourceId(payoutRequestId)
                .amountScoin(amountScoin)
                .balanceEffectScoin(-amountScoin)
                .memo(memo)
                .build());
    }

    @Transactional
    public void voidPayoutHold(UUID mentorUserId, UUID payoutRequestId, String memo) {
        SettlementAccount account = ensureMentorAccount(mentorUserId);
        if (settlementEntryRepository.findFirstByAccountIdAndSourceTypeAndSourceIdAndEntryTypeOrderByCreatedAtDesc(
                account.getId(), LedgerSourceType.PAYOUT_REQUEST, payoutRequestId, SettlementEntryType.VOID
        ).isPresent()) {
            return;
        }
        settlementEntryRepository.findFirstByAccountIdAndSourceTypeAndSourceIdAndEntryTypeOrderByCreatedAtDesc(
                account.getId(), LedgerSourceType.PAYOUT_REQUEST, payoutRequestId, SettlementEntryType.HOLD
        ).ifPresent(hold -> settlementEntryRepository.save(SettlementEntry.builder()
                .accountId(account.getId())
                .entryType(SettlementEntryType.VOID)
                .sourceType(LedgerSourceType.PAYOUT_REQUEST)
                .sourceId(payoutRequestId)
                .amountScoin(hold.getAmountScoin())
                .balanceEffectScoin(hold.getAmountScoin())
                .memo(memo)
                .build()));
    }
}
