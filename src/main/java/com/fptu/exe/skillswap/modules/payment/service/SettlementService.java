package com.fptu.exe.skillswap.modules.payment.service;

import com.fptu.exe.skillswap.infrastructure.config.PaymentProperties;
import com.fptu.exe.skillswap.modules.booking.domain.Booking;
import com.fptu.exe.skillswap.modules.booking.domain.BookingCompletionOutcome;
import com.fptu.exe.skillswap.modules.booking.domain.BookingStatus;
import com.fptu.exe.skillswap.modules.payment.domain.LedgerAccountType;
import com.fptu.exe.skillswap.modules.payment.domain.LedgerEntryType;
import com.fptu.exe.skillswap.modules.payment.domain.LedgerSourceType;
import com.fptu.exe.skillswap.modules.payment.domain.SettlementAccount;
import com.fptu.exe.skillswap.modules.payment.domain.SettlementEntry;
import com.fptu.exe.skillswap.modules.payment.domain.SettlementEntryType;
import com.fptu.exe.skillswap.modules.payment.domain.PaymentOrder;
import com.fptu.exe.skillswap.modules.payment.domain.PaymentOrderStatus;
import com.fptu.exe.skillswap.modules.payment.domain.CreditOriginType;
import com.fptu.exe.skillswap.modules.payment.domain.PaymentSettlementStatus;
import com.fptu.exe.skillswap.shared.util.DateTimeUtil;
import com.fptu.exe.skillswap.modules.payment.repository.CreditLedgerEntryRepository;
import com.fptu.exe.skillswap.modules.payment.repository.PaymentOrderRepository;
import com.fptu.exe.skillswap.modules.payment.repository.SettlementAccountRepository;
import com.fptu.exe.skillswap.modules.payment.repository.SettlementEntryRepository;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SettlementService {

    private static final int COMMISSION_BPS_DEFAULT = 1000;
    private static final UUID PLATFORM_OWNER_ID = new UUID(0L, 1L);
    private static final int LATE_MENTEE_CANCEL_REFUND_BPS = 5000;
    private static final int LATE_MENTEE_CANCEL_MENTOR_BPS = 3500;
    private static final int LATE_MENTEE_CANCEL_PLATFORM_BPS = 1500;

    private final SettlementAccountRepository settlementAccountRepository;
    private final SettlementEntryRepository settlementEntryRepository;
    private final CreditLedgerEntryRepository creditLedgerEntryRepository;
    private final PaymentOrderRepository paymentOrderRepository;
    private final PaymentProperties paymentProperties;
    private final CreditLedgerService creditLedgerService;

    @Retryable(value = ObjectOptimisticLockingFailureException.class, maxAttempts = 3, backoff = @Backoff(delay = 100))
    @Transactional
    public SettlementAccount ensureMentorAccount(UUID mentorUserId) {
        if (mentorUserId == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "mentorUserId không được để trống");
        }
        return ensureAccount(LedgerAccountType.MENTOR_SETTLEMENT, mentorUserId, "SETTLEMENT_MENTOR_" + mentorUserId);
    }

    @Retryable(value = ObjectOptimisticLockingFailureException.class, maxAttempts = 3, backoff = @Backoff(delay = 100))
    @Transactional
    public SettlementAccount ensurePlatformAccount() {
        return ensureAccount(LedgerAccountType.PLATFORM_SETTLEMENT, PLATFORM_OWNER_ID, "SETTLEMENT_PLATFORM");
    }

    @Retryable(value = ObjectOptimisticLockingFailureException.class, maxAttempts = 3, backoff = @Backoff(delay = 100))
    @Transactional
    public void releaseForBooking(Booking booking) {
        if (booking == null || booking.getId() == null) {
            return;
        }
        if (booking.getMentorProfile() == null || booking.getMentorProfile().getUserId() == null) {
            return;
        }
        if (booking.getStatus() != BookingStatus.COMPLETED) {
            return;
        }
        if (booking.getCompletionOutcome() != BookingCompletionOutcome.USER_CONFIRMED
                && booking.getCompletionOutcome() != BookingCompletionOutcome.AUTO_CLOSED
                && booking.getCompletionOutcome() != BookingCompletionOutcome.NO_SHOW_MENTEE) {
            return;
        }
        PaymentOrder paymentOrder = paymentOrderRepository.findByBookingIdForUpdate(booking.getId()).orElse(null);
        if (paymentOrder == null || paymentOrder.getStatus() != PaymentOrderStatus.PAID) {
            return;
        }
        if (paymentOrder.getSettlementStatus() == PaymentSettlementStatus.RELEASED) {
            return;
        }
        if (paymentOrder.getSettlementStatus() == PaymentSettlementStatus.REFUNDED) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Payment đã được hoàn tiền, không thể release settlement");
        }
        SettlementAccount mentorAccount = lockMentorAccount(booking.getMentorProfile().getUserId());
        if (settlementEntryRepository.findFirstByAccountIdAndSourceTypeAndSourceIdAndEntryTypeOrderByCreatedAtDesc(
                mentorAccount.getId(),
                LedgerSourceType.BOOKING,
                booking.getId(),
                SettlementEntryType.RELEASE
        ).isPresent()) {
            return;
        }

        int grossScoin = Math.max(0, paymentOrder.getGrossScoin() == null ? 0 : paymentOrder.getGrossScoin());
        int commissionBps = paymentOrder.getCommissionRateBps() == null || paymentOrder.getCommissionRateBps() <= 0
                ? (paymentProperties == null ? COMMISSION_BPS_DEFAULT : paymentProperties.getPlatformCommissionBps())
                : paymentOrder.getCommissionRateBps();
        int commissionScoin = Math.max(0, paymentOrder.getCommissionScoin() == null
                ? (grossScoin * commissionBps) / 10_000
                : paymentOrder.getCommissionScoin());
        int releasableScoin = Math.max(0, paymentOrder.getMentorNetScoin() == null
                ? grossScoin - commissionScoin
                : paymentOrder.getMentorNetScoin());

        SettlementAccount platformAccount = lockPlatformAccount();

        settlementAccountRepository.addBalance(mentorAccount.getId(), java.math.BigDecimal.valueOf(releasableScoin));
        settlementEntryRepository.save(SettlementEntry.builder()
                .accountId(mentorAccount.getId())
                .entryType(SettlementEntryType.RELEASE)
                .sourceType(LedgerSourceType.BOOKING)
                .sourceId(booking.getId())
                .amountScoin(releasableScoin)
                .balanceEffectScoin(releasableScoin)
                .grossScoin(grossScoin)
                .commissionRateBps(commissionBps)
                .commissionScoin(commissionScoin)
                .mentorNetScoin(releasableScoin)
                .memo("Release for completed booking " + booking.getId())
                .build());

        settlementAccountRepository.addBalance(platformAccount.getId(), java.math.BigDecimal.valueOf(commissionScoin));
        settlementEntryRepository.save(SettlementEntry.builder()
                .accountId(platformAccount.getId())
                .entryType(SettlementEntryType.COMMISSION)
                .sourceType(LedgerSourceType.BOOKING)
                .sourceId(booking.getId())
                .amountScoin(commissionScoin)
                .balanceEffectScoin(commissionScoin)
                .grossScoin(grossScoin)
                .commissionRateBps(commissionBps)
                .commissionScoin(commissionScoin)
                .mentorNetScoin(releasableScoin)
                .memo("Platform commission for booking " + booking.getId())
                .build());
        paymentOrder.setSettlementStatus(PaymentSettlementStatus.RELEASED);
        paymentOrder.setReleasedAt(DateTimeUtil.now());
        paymentOrderRepository.save(paymentOrder);
    }

    /** Full refund used only for the resolved mentor no-show path. */
    @Retryable(value = ObjectOptimisticLockingFailureException.class, maxAttempts = 3, backoff = @Backoff(delay = 100))
    @Transactional
    public void refundForMentorNoShow(Booking booking) {
        if (booking == null || booking.getId() == null || booking.getMentee() == null) {
            return;
        }
        PaymentOrder paymentOrder = paymentOrderRepository.findByBookingIdForUpdate(booking.getId()).orElse(null);
        if (paymentOrder == null || paymentOrder.getStatus() != PaymentOrderStatus.PAID) {
            return;
        }
        if (paymentOrder.getSettlementStatus() == PaymentSettlementStatus.REFUNDED) {
            return;
        }
        if (paymentOrder.getSettlementStatus() == PaymentSettlementStatus.RELEASED) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Settlement đã release, không thể hoàn tiền tự động");
        }
        int amount = Math.max(0, paymentOrder.getGrossScoin() == null ? 0 : paymentOrder.getGrossScoin());
        if (amount == 0) {
            paymentOrder.setSettlementStatus(PaymentSettlementStatus.REFUNDED);
            paymentOrder.setRefundedAt(DateTimeUtil.now());
            paymentOrder.setRefundedScoin(0);
            paymentOrder.setRefundReason("MENTOR_NO_SHOW");
            paymentOrderRepository.save(paymentOrder);
            return;
        }
        String operationKey = "PAYMENT_REFUND:" + paymentOrder.getId();
        creditLedgerService.refundCredit(
                booking.getMentee().getId(), CreditOriginType.REFUND, LedgerSourceType.BOOKING,
                booking.getId(), amount, "Full refund for mentor no-show booking " + booking.getId(), operationKey);
        paymentOrder.setSettlementStatus(PaymentSettlementStatus.REFUNDED);
        paymentOrder.setRefundedAt(DateTimeUtil.now());
        paymentOrder.setRefundedScoin(amount);
        paymentOrder.setRefundReason("MENTOR_NO_SHOW");
        paymentOrderRepository.save(paymentOrder);
    }

    @Retryable(value = ObjectOptimisticLockingFailureException.class, maxAttempts = 3, backoff = @Backoff(delay = 100))
    @Transactional
    public void handlePaidBookingCancelledByMentee(Booking booking, PaymentOrder paymentOrder, boolean lateCancellation) {
        if (booking == null || booking.getId() == null || booking.getMentee() == null || booking.getMentee().getId() == null) {
            return;
        }
        if (paymentOrder == null || paymentOrder.getStatus() != PaymentOrderStatus.PAID) {
            return;
        }

        var creditAccount = creditLedgerService.getUserAccountForUpdate(booking.getMentee().getId());
        boolean refundAlreadyRecorded = creditLedgerEntryRepository
                .findFirstByAccountIdAndSourceTypeAndSourceIdAndEntryTypeOrderByCreatedAtDesc(
                        creditAccount.getId(),
                        LedgerSourceType.BOOKING,
                        booking.getId(),
                        LedgerEntryType.REFUND
                )
                .isPresent();
        if (refundAlreadyRecorded) {
            return;
        }

        int grossScoin = Math.max(0, paymentOrder.getGrossScoin() == null ? 0 : paymentOrder.getGrossScoin());
        if (grossScoin <= 0) {
            return;
        }

        if (!lateCancellation) {
            creditLedgerService.refundCredit(
                    booking.getMentee().getId(),
                    CreditOriginType.REFUND,
                    LedgerSourceType.BOOKING,
                    booking.getId(),
                    grossScoin,
                    "Full refund for early mentee cancellation of booking " + booking.getId()
            );
            return;
        }

        int mentorShare = (grossScoin * LATE_MENTEE_CANCEL_MENTOR_BPS) / 10_000;
        int platformShare = (grossScoin * LATE_MENTEE_CANCEL_PLATFORM_BPS) / 10_000;
        int refundShare = Math.max(0, grossScoin - mentorShare - platformShare);

        if (refundShare > 0) {
            creditLedgerService.refundCredit(
                    booking.getMentee().getId(),
                    CreditOriginType.REFUND,
                    LedgerSourceType.BOOKING,
                    booking.getId(),
                    refundShare,
                    "50% refund for late mentee cancellation of booking " + booking.getId()
            );
        }

        SettlementAccount mentorAccount = lockMentorAccount(paymentOrder.getMentorUserId());
        SettlementAccount platformAccount = lockPlatformAccount();

        if (mentorShare > 0) {
            settlementAccountRepository.addBalance(mentorAccount.getId(), java.math.BigDecimal.valueOf(mentorShare));
            settlementEntryRepository.save(SettlementEntry.builder()
                    .accountId(mentorAccount.getId())
                    .entryType(SettlementEntryType.RELEASE)
                    .sourceType(LedgerSourceType.BOOKING)
                    .sourceId(booking.getId())
                    .amountScoin(mentorShare)
                    .balanceEffectScoin(mentorShare)
                    .grossScoin(grossScoin)
                    .commissionRateBps(LATE_MENTEE_CANCEL_PLATFORM_BPS)
                    .commissionScoin(platformShare)
                    .mentorNetScoin(mentorShare)
                    .memo("Late mentee cancellation compensation for booking " + booking.getId())
                    .build());
        }

        if (platformShare > 0) {
            settlementAccountRepository.addBalance(platformAccount.getId(), java.math.BigDecimal.valueOf(platformShare));
            settlementEntryRepository.save(SettlementEntry.builder()
                    .accountId(platformAccount.getId())
                    .entryType(SettlementEntryType.COMMISSION)
                    .sourceType(LedgerSourceType.BOOKING)
                    .sourceId(booking.getId())
                    .amountScoin(platformShare)
                    .balanceEffectScoin(platformShare)
                    .grossScoin(grossScoin)
                    .commissionRateBps(LATE_MENTEE_CANCEL_PLATFORM_BPS)
                    .commissionScoin(platformShare)
                    .mentorNetScoin(mentorShare)
                    .memo("Platform commission from late mentee cancellation of booking " + booking.getId())
                    .build());
        }
    }

    @Retryable(value = ObjectOptimisticLockingFailureException.class, maxAttempts = 3, backoff = @Backoff(delay = 100))
    @Transactional
    public void handlePaidBookingCancelledByMentor(Booking booking, PaymentOrder paymentOrder) {
        if (booking == null || booking.getId() == null || booking.getMentee() == null || booking.getMentee().getId() == null) {
            return;
        }
        if (paymentOrder == null || paymentOrder.getStatus() != PaymentOrderStatus.PAID) {
            return;
        }

        var creditAccount = creditLedgerService.getUserAccountForUpdate(booking.getMentee().getId());
        boolean refundAlreadyRecorded = creditLedgerEntryRepository
                .findFirstByAccountIdAndSourceTypeAndSourceIdAndEntryTypeOrderByCreatedAtDesc(
                        creditAccount.getId(),
                        LedgerSourceType.BOOKING,
                        booking.getId(),
                        LedgerEntryType.REFUND
                )
                .isPresent();
        if (refundAlreadyRecorded) {
            return;
        }

        int grossScoin = Math.max(0, paymentOrder.getGrossScoin() == null ? 0 : paymentOrder.getGrossScoin());
        if (grossScoin <= 0) {
            return;
        }

        creditLedgerService.refundCredit(
                booking.getMentee().getId(),
                CreditOriginType.REFUND,
                LedgerSourceType.BOOKING,
                booking.getId(),
                grossScoin,
                "Full refund because mentor cancelled booking " + booking.getId()
        );
    }

    @Transactional(readOnly = true)
    public int getMentorAvailableSettlement(UUID mentorUserId) {
        SettlementAccount account = ensureMentorAccount(mentorUserId);
        return settlementEntryRepository.sumBalanceEffectByAccountId(account.getId()).intValue();
    }

    @Transactional(readOnly = true)
    public java.util.List<SettlementEntry> getRecentTransactions(UUID mentorUserId) {
        SettlementAccount account = ensureMentorAccount(mentorUserId);
        return settlementEntryRepository.findTop15ByAccountIdOrderByCreatedAtDesc(account.getId());
    }

    @Retryable(value = ObjectOptimisticLockingFailureException.class, maxAttempts = 3, backoff = @Backoff(delay = 100))
    @Transactional
    public SettlementEntry holdPayout(UUID mentorUserId, UUID payoutRequestId, int amountScoin, String memo) {
        if (amountScoin <= 0) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "amountScoin phải lớn hơn 0");
        }
        SettlementAccount account = lockMentorAccount(mentorUserId);
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

        int rows = settlementAccountRepository.deductBalanceSafely(account.getId(), java.math.BigDecimal.valueOf(amountScoin));
        if (rows == 0) {
            throw new BaseException(ErrorCode.INSUFFICIENT_BALANCE, "Số dư không đủ để thực hiện hold");
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

    @Retryable(value = ObjectOptimisticLockingFailureException.class, maxAttempts = 3, backoff = @Backoff(delay = 100))
    @Transactional
    public void voidPayoutHold(UUID mentorUserId, UUID payoutRequestId, String memo) {
        SettlementAccount account = lockMentorAccount(mentorUserId);
        if (settlementEntryRepository.findFirstByAccountIdAndSourceTypeAndSourceIdAndEntryTypeOrderByCreatedAtDesc(
                account.getId(), LedgerSourceType.PAYOUT_REQUEST, payoutRequestId, SettlementEntryType.VOID
        ).isPresent()) {
            return;
        }
        settlementEntryRepository.findFirstByAccountIdAndSourceTypeAndSourceIdAndEntryTypeOrderByCreatedAtDesc(
                account.getId(), LedgerSourceType.PAYOUT_REQUEST, payoutRequestId, SettlementEntryType.HOLD
        ).ifPresent(hold -> {
            settlementAccountRepository.addBalance(account.getId(), java.math.BigDecimal.valueOf(hold.getAmountScoin()));
            settlementEntryRepository.save(SettlementEntry.builder()
                    .accountId(account.getId())
                .entryType(SettlementEntryType.VOID)
                .sourceType(LedgerSourceType.PAYOUT_REQUEST)
                .sourceId(payoutRequestId)
                .amountScoin(hold.getAmountScoin())
                .balanceEffectScoin(hold.getAmountScoin())
                .memo(memo)
                .build());
        });
    }

    @Retryable(value = ObjectOptimisticLockingFailureException.class, maxAttempts = 3, backoff = @Backoff(delay = 100))
    @Transactional
    public void finalizePayout(UUID mentorUserId, UUID payoutRequestId, String memo) {
        SettlementAccount account = lockMentorAccount(mentorUserId);
        if (settlementEntryRepository.findFirstByAccountIdAndSourceTypeAndSourceIdAndEntryTypeOrderByCreatedAtDesc(
                account.getId(), LedgerSourceType.PAYOUT_REQUEST, payoutRequestId, SettlementEntryType.PAID_OUT
        ).isPresent()) {
            return;
        }
        settlementEntryRepository.findFirstByAccountIdAndSourceTypeAndSourceIdAndEntryTypeOrderByCreatedAtDesc(
                account.getId(), LedgerSourceType.PAYOUT_REQUEST, payoutRequestId, SettlementEntryType.HOLD
        ).ifPresent(hold -> settlementEntryRepository.save(SettlementEntry.builder()
                .accountId(account.getId())
                .entryType(SettlementEntryType.PAID_OUT)
                .sourceType(LedgerSourceType.PAYOUT_REQUEST)
                .sourceId(payoutRequestId)
                .amountScoin(hold.getAmountScoin())
                .balanceEffectScoin(0)
                .memo(memo)
                .build()));
    }

    private SettlementAccount ensureAccount(LedgerAccountType ownerType, UUID ownerId, String accountCode) {
        return settlementAccountRepository.findByOwnerTypeAndOwnerId(ownerType, ownerId)
                .orElseGet(() -> createAccount(ownerType, ownerId, accountCode));
    }

    private SettlementAccount lockMentorAccount(UUID mentorUserId) {
        if (mentorUserId == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "mentorUserId không được để trống");
        }
        return lockAccount(LedgerAccountType.MENTOR_SETTLEMENT, mentorUserId, "SETTLEMENT_MENTOR_" + mentorUserId);
    }

    private SettlementAccount lockPlatformAccount() {
        return lockAccount(LedgerAccountType.PLATFORM_SETTLEMENT, PLATFORM_OWNER_ID, "SETTLEMENT_PLATFORM");
    }

    private SettlementAccount lockAccount(LedgerAccountType ownerType, UUID ownerId, String accountCode) {
        SettlementAccount existing = settlementAccountRepository.findByOwnerTypeAndOwnerId(ownerType, ownerId)
                .orElse(null);
        if (existing != null) {
            return existing;
        }
        ensureAccount(ownerType, ownerId, accountCode);
        return settlementAccountRepository.findByOwnerTypeAndOwnerId(ownerType, ownerId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không thể khóa settlement account"));
    }

    private SettlementAccount createAccount(LedgerAccountType ownerType, UUID ownerId, String accountCode) {
        try {
            return settlementAccountRepository.save(SettlementAccount.builder()
                    .ownerType(ownerType)
                    .ownerId(ownerId)
                    .accountCode(accountCode)
                    .build());
        } catch (DataIntegrityViolationException ex) {
            return settlementAccountRepository.findByOwnerTypeAndOwnerId(ownerType, ownerId)
                    .orElseThrow(() -> ex);
        }
    }
}
