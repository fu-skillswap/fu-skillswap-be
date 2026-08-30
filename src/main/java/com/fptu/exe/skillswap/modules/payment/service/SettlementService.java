package com.fptu.exe.skillswap.modules.payment.service;

import com.fptu.exe.skillswap.shared.policy.PricingPolicy;

import com.fptu.exe.skillswap.infrastructure.config.PaymentProperties;
import com.fptu.exe.skillswap.modules.booking.domain.Booking;
import com.fptu.exe.skillswap.modules.booking.domain.BookingCompletionOutcome;
import com.fptu.exe.skillswap.modules.booking.domain.BookingIssueResolution;
import com.fptu.exe.skillswap.modules.booking.domain.AdminBookingIssueResolutionAction;
import com.fptu.exe.skillswap.modules.booking.domain.BookingStatus;
import com.fptu.exe.skillswap.modules.payment.domain.LedgerAccountType;
import com.fptu.exe.skillswap.modules.payment.domain.LedgerEntryType;
import com.fptu.exe.skillswap.modules.payment.domain.LedgerSourceType;
import com.fptu.exe.skillswap.modules.payment.domain.SettlementAccount;
import com.fptu.exe.skillswap.modules.payment.domain.SettlementEntry;
import com.fptu.exe.skillswap.modules.payment.domain.SettlementEntryType;
import com.fptu.exe.skillswap.modules.payment.domain.PaymentOrder;
import com.fptu.exe.skillswap.modules.payment.domain.PaymentOrderStatus;
import com.fptu.exe.skillswap.modules.payment.domain.PaymentTargetType;
import com.fptu.exe.skillswap.modules.payment.domain.CreditOriginType;
import com.fptu.exe.skillswap.modules.payment.domain.PaymentSettlementStatus;
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

import com.fptu.exe.skillswap.shared.time.TimeProvider;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SettlementService {

    private static final UUID PLATFORM_OWNER_ID = new UUID(0L, 1L);

    private final SettlementAccountRepository settlementAccountRepository;
    private final SettlementEntryRepository settlementEntryRepository;
    private final CreditLedgerEntryRepository creditLedgerEntryRepository;
    private final PaymentOrderRepository paymentOrderRepository;
    private final PaymentProperties paymentProperties;
    private final CreditLedgerService creditLedgerService;

    private TimeProvider timeProvider = TimeProvider.from(Clock.systemUTC());

    @Autowired(required = false)
    public void setTimeProvider(TimeProvider timeProvider) {
        if (timeProvider != null) {
            this.timeProvider = timeProvider;
        }
    }

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
                && booking.getCompletionOutcome() != BookingCompletionOutcome.NO_SHOW_MENTEE
                && booking.getCompletionOutcome() != BookingCompletionOutcome.ADMIN_SLA_AUTO_RELEASED) {
            return;
        }
        PaymentOrder paymentOrder = paymentOrderRepository.findByTargetTypeAndTargetIdForUpdate(PaymentTargetType.BOOKING, booking.getId()).orElse(null);
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
                ? paymentProperties.getPlatformCommissionBps()
                : paymentOrder.getCommissionRateBps();
        int commissionScoin = Math.max(0, paymentOrder.getCommissionScoin() == null
                ? PricingPolicy.bpsAmount(grossScoin, commissionBps)
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
        paymentOrder.setReleasedAtUtc(timeProvider.instant());
        paymentOrder.setReleasedAt(timeProvider.nowBusiness());
        paymentOrderRepository.save(paymentOrder);
    }

    /**
     * Applies the financial side of one immutable admin dispute decision. The booking must
     * already be locked by the caller. The resolution UUID is deliberately the ledger source and
     * refund operation key, making retry of the same decision harmless without conflating it with
     * an ordinary booking release.
     */
    @Transactional
    public void applyAdminIssueResolution(Booking booking, BookingIssueResolution resolution) {
        if (booking == null || booking.getId() == null || resolution == null || resolution.getId() == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Thiếu booking hoặc quyết định dispute để settlement");
        }
        PaymentOrder paymentOrder = paymentOrderRepository
                .findByTargetTypeAndTargetIdForUpdate(PaymentTargetType.BOOKING, booking.getId())
                .orElse(null);
        if (paymentOrder == null) {
            // A free booking still needs the immutable decision/session outcome, but has no escrow.
            setResolutionAmounts(resolution, 0, 0, 0, 0);
            resolution.setSettlementAppliedAtUtc(timeProvider.instant());
            return;
        }
        if (paymentOrder.getStatus() != PaymentOrderStatus.PAID
                || paymentOrder.getSettlementStatus() != PaymentSettlementStatus.HELD) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT,
                    "Chỉ payment đã thanh toán và đang được giữ mới có thể resolve dispute");
        }

        int escrow = nonNegative(paymentOrder.getGrossScoin());
        Allocation allocation = allocationFor(paymentOrder, resolution, escrow);
        if (allocation.total() != escrow) {
            throw new BaseException(ErrorCode.DATABASE_ERROR, "Settlement dispute không cân bằng số tiền ký quỹ");
        }

        if (allocation.menteeRefund() > 0) {
            creditLedgerService.refundCredit(
                    booking.getMentee().getId(),
                    CreditOriginType.REFUND,
                    LedgerSourceType.BOOKING_ISSUE_RESOLUTION,
                    resolution.getId(),
                    allocation.menteeRefund(),
                    "Dispute settlement refund for booking " + booking.getId(),
                    "BOOKING_ISSUE_RESOLUTION_REFUND:" + resolution.getId()
            );
        }

        SettlementAccount mentorAccount = allocation.mentorSettlement() > 0
                ? lockMentorAccount(paymentOrder.getMentorUserId()) : null;
        SettlementAccount platformAccount = allocation.platformSettlement() > 0
                ? lockPlatformAccount() : null;
        if (mentorAccount != null) {
            settlementAccountRepository.addBalance(mentorAccount.getId(), BigDecimal.valueOf(allocation.mentorSettlement()));
            settlementEntryRepository.save(SettlementEntry.builder()
                    .accountId(mentorAccount.getId())
                    .entryType(SettlementEntryType.RELEASE)
                    .sourceType(LedgerSourceType.BOOKING_ISSUE_RESOLUTION)
                    .sourceId(resolution.getId())
                    .amountScoin(allocation.mentorSettlement())
                    .balanceEffectScoin(allocation.mentorSettlement())
                    .grossScoin(escrow)
                    .commissionScoin(allocation.platformSettlement())
                    .mentorNetScoin(allocation.mentorSettlement())
                    .memo("Admin dispute " + resolution.getAction() + " for booking " + booking.getId())
                    .build());
        }
        if (platformAccount != null) {
            settlementAccountRepository.addBalance(platformAccount.getId(), BigDecimal.valueOf(allocation.platformSettlement()));
            settlementEntryRepository.save(SettlementEntry.builder()
                    .accountId(platformAccount.getId())
                    .entryType(SettlementEntryType.COMMISSION)
                    .sourceType(LedgerSourceType.BOOKING_ISSUE_RESOLUTION)
                    .sourceId(resolution.getId())
                    .amountScoin(allocation.platformSettlement())
                    .balanceEffectScoin(allocation.platformSettlement())
                    .grossScoin(escrow)
                    .commissionScoin(allocation.platformSettlement())
                    .mentorNetScoin(allocation.mentorSettlement())
                    .memo("Admin dispute platform allocation for booking " + booking.getId())
                    .build());
        }

        setResolutionAmounts(resolution, escrow, allocation.menteeRefund(),
                allocation.mentorSettlement(), allocation.platformSettlement());
        resolution.setSettlementAppliedAtUtc(timeProvider.instant());
        if (allocation.menteeRefund() == escrow) {
            paymentOrder.setSettlementStatus(PaymentSettlementStatus.REFUNDED);
            paymentOrder.setRefundedScoin(escrow);
            paymentOrder.setRefundReason("ADMIN_DISPUTE_" + resolution.getAction());
            paymentOrder.setRefundedAtUtc(timeProvider.instant());
            paymentOrder.setRefundedAt(timeProvider.nowBusiness());
        } else if (resolution.getAction() == AdminBookingIssueResolutionAction.PARTIAL_SETTLEMENT) {
            paymentOrder.setSettlementStatus(PaymentSettlementStatus.PARTIALLY_SETTLED);
            paymentOrder.setRefundedScoin(allocation.menteeRefund());
            paymentOrder.setRefundReason("ADMIN_DISPUTE_PARTIAL_SETTLEMENT");
            paymentOrder.setRefundedAtUtc(timeProvider.instant());
            paymentOrder.setRefundedAt(timeProvider.nowBusiness());
        } else {
            paymentOrder.setSettlementStatus(PaymentSettlementStatus.RELEASED);
            paymentOrder.setReleasedAtUtc(timeProvider.instant());
            paymentOrder.setReleasedAt(timeProvider.nowBusiness());
        }
        paymentOrderRepository.save(paymentOrder);
    }

    private Allocation allocationFor(PaymentOrder paymentOrder, BookingIssueResolution resolution, int escrow) {
        if (resolution.getAction() == AdminBookingIssueResolutionAction.CONFIRM_MENTOR_NO_SHOW_REFUND) {
            return new Allocation(escrow, 0, 0);
        }
        if (resolution.getAction() == AdminBookingIssueResolutionAction.PARTIAL_SETTLEMENT) {
            int mentee = PricingPolicy.bpsAmount(escrow, nonNegative(resolution.getMenteeBps()));
            int mentor = PricingPolicy.bpsAmount(escrow, nonNegative(resolution.getMentorBps()));
            // The platform absorbs only integer division remainder; the submitted BPS are still
            // validated to 100% by the booking policy before this method is reached.
            return new Allocation(mentee, mentor, Math.max(0, escrow - mentee - mentor));
        }
        int mentor = nonNegative(paymentOrder.getMentorNetScoin());
        if (mentor == 0 && escrow > 0) {
            int commission = paymentOrder.getCommissionScoin() == null
                    ? PricingPolicy.bpsAmount(escrow, nonNegative(paymentOrder.getCommissionRateBps()))
                    : nonNegative(paymentOrder.getCommissionScoin());
            mentor = Math.max(0, escrow - commission);
        }
        mentor = Math.min(mentor, escrow);
        return new Allocation(0, mentor, escrow - mentor);
    }

    private void setResolutionAmounts(BookingIssueResolution resolution, int escrow, int mentee, int mentor, int platform) {
        resolution.setEscrowScoin(escrow);
        resolution.setMenteeRefundScoin(mentee);
        resolution.setMentorSettlementScoin(mentor);
        resolution.setPlatformSettlementScoin(platform);
    }

    private int nonNegative(Integer amount) {
        return amount == null ? 0 : Math.max(0, amount);
    }

    private record Allocation(int menteeRefund, int mentorSettlement, int platformSettlement) {
        private int total() {
            return Math.addExact(Math.addExact(menteeRefund, mentorSettlement), platformSettlement);
        }
    }

    /**
     * Compensating settlement entries for an admin resolution reversal. The reversal record UUID
     * is the ledger source, making retry idempotent without conflating with the original decision.
     *
     * <p>If the mentor's settlement balance is insufficient to debit (e.g. already paid out),
     * the reversal record is moved to MANUAL_FINANCE_REVIEW and no balance adjustments are made.
     * The caller must handle the MANUAL_FINANCE_REVIEW status accordingly.</p>
     */
    @Transactional
    public void applyReversal(Booking booking,
                              BookingIssueResolution originalResolution,
                              BookingIssueResolution reversalRecord) {
        if (booking == null || booking.getId() == null
                || originalResolution == null || originalResolution.getId() == null
                || reversalRecord == null || reversalRecord.getId() == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Thiếu booking hoặc quyết định dispute để reversal");
        }

        PaymentOrder paymentOrder = paymentOrderRepository
                .findByTargetTypeAndTargetIdForUpdate(PaymentTargetType.BOOKING, booking.getId())
                .orElse(null);
        if (paymentOrder == null) {
            // Free booking: no financial entries to reverse, just mark settlement applied.
            reversalRecord.setEscrowScoin(0);
            reversalRecord.setMenteeRefundScoin(0);
            reversalRecord.setMentorSettlementScoin(0);
            reversalRecord.setPlatformSettlementScoin(0);
            reversalRecord.setSettlementAppliedAtUtc(timeProvider.instant());
            return;
        }

        int mentorDebit = nonNegative(originalResolution.getMentorSettlementScoin());
        int platformDebit = nonNegative(originalResolution.getPlatformSettlementScoin());
        int menteeDebit = nonNegative(originalResolution.getMenteeRefundScoin());

        // Verify mentor and mentee have sufficient balances before committing any debits.
        if (mentorDebit > 0) {
            SettlementAccount mentorAccount = lockMentorAccount(paymentOrder.getMentorUserId());
            int mentorBalance = settlementBalance(mentorAccount);
            if (mentorBalance < mentorDebit) {
                reversalRecord.setStatus(com.fptu.exe.skillswap.modules.booking.domain.BookingIssueResolutionStatus.MANUAL_FINANCE_REVIEW);
                reversalRecord.setEscrowScoin(nonNegative(originalResolution.getEscrowScoin()));
                reversalRecord.setMenteeRefundScoin(menteeDebit);
                reversalRecord.setMentorSettlementScoin(mentorDebit);
                reversalRecord.setPlatformSettlementScoin(platformDebit);
                reversalRecord.setSettlementAppliedAtUtc(timeProvider.instant());
                return;
            }
        }

        if (menteeDebit > 0) {
            int menteeAvailable = creditLedgerService.getAvailableBalance(booking.getMentee().getId());
            if (menteeAvailable < menteeDebit) {
                reversalRecord.setStatus(com.fptu.exe.skillswap.modules.booking.domain.BookingIssueResolutionStatus.MANUAL_FINANCE_REVIEW);
                reversalRecord.setEscrowScoin(nonNegative(originalResolution.getEscrowScoin()));
                reversalRecord.setMenteeRefundScoin(menteeDebit);
                reversalRecord.setMentorSettlementScoin(mentorDebit);
                reversalRecord.setPlatformSettlementScoin(platformDebit);
                reversalRecord.setSettlementAppliedAtUtc(timeProvider.instant());
                return;
            }
        }

        if (mentorDebit > 0) {
            SettlementAccount mentorAccount = lockMentorAccount(paymentOrder.getMentorUserId());
            int rows = settlementAccountRepository.deductBalanceSafely(mentorAccount.getId(), BigDecimal.valueOf(mentorDebit));
            if (rows == 0) {
                reversalRecord.setStatus(com.fptu.exe.skillswap.modules.booking.domain.BookingIssueResolutionStatus.MANUAL_FINANCE_REVIEW);
                reversalRecord.setEscrowScoin(nonNegative(originalResolution.getEscrowScoin()));
                reversalRecord.setMenteeRefundScoin(menteeDebit);
                reversalRecord.setMentorSettlementScoin(mentorDebit);
                reversalRecord.setPlatformSettlementScoin(platformDebit);
                reversalRecord.setSettlementAppliedAtUtc(timeProvider.instant());
                return;
            }
            settlementEntryRepository.save(SettlementEntry.builder()
                    .accountId(mentorAccount.getId())
                    .entryType(SettlementEntryType.ADJUSTMENT)
                    .sourceType(LedgerSourceType.BOOKING_ISSUE_RESOLUTION)
                    .sourceId(reversalRecord.getId())
                    .amountScoin(mentorDebit)
                    .balanceEffectScoin(-mentorDebit)
                    .grossScoin(nonNegative(originalResolution.getEscrowScoin()))
                    .mentorNetScoin(-mentorDebit)
                    .memo("Reversal debit for booking " + booking.getId() + " resolution " + originalResolution.getId())
                    .build());
        }

        if (platformDebit > 0) {
            SettlementAccount platformAccount = lockPlatformAccount();
            settlementAccountRepository.addBalance(platformAccount.getId(), BigDecimal.valueOf(-platformDebit));
            settlementEntryRepository.save(SettlementEntry.builder()
                    .accountId(platformAccount.getId())
                    .entryType(SettlementEntryType.ADJUSTMENT)
                    .sourceType(LedgerSourceType.BOOKING_ISSUE_RESOLUTION)
                    .sourceId(reversalRecord.getId())
                    .amountScoin(platformDebit)
                    .balanceEffectScoin(-platformDebit)
                    .grossScoin(nonNegative(originalResolution.getEscrowScoin()))
                    .commissionScoin(-platformDebit)
                    .memo("Reversal platform debit for booking " + booking.getId() + " resolution " + originalResolution.getId())
                    .build());
        }

        if (menteeDebit > 0) {
            boolean debited = creditLedgerService.debitCredit(
                    booking.getMentee().getId(),
                    CreditOriginType.REFUND,
                    LedgerSourceType.BOOKING_ISSUE_RESOLUTION,
                    reversalRecord.getId(),
                    menteeDebit,
                    "Reversal debit of prior dispute refund for booking " + booking.getId(),
                    "BOOKING_ISSUE_RESOLUTION_REVERSAL_DEBIT:" + reversalRecord.getId()
            );
            if (!debited) {
                reversalRecord.setStatus(com.fptu.exe.skillswap.modules.booking.domain.BookingIssueResolutionStatus.MANUAL_FINANCE_REVIEW);
                reversalRecord.setEscrowScoin(nonNegative(originalResolution.getEscrowScoin()));
                reversalRecord.setMenteeRefundScoin(menteeDebit);
                reversalRecord.setMentorSettlementScoin(mentorDebit);
                reversalRecord.setPlatformSettlementScoin(platformDebit);
                reversalRecord.setSettlementAppliedAtUtc(timeProvider.instant());
                return;
            }
        }

        // Return payment order to HELD so a replacement decision can settle fresh.
        paymentOrder.setSettlementStatus(PaymentSettlementStatus.HELD);
        paymentOrder.setReleasedAtUtc(null);
        paymentOrder.setReleasedAt(null);
        paymentOrderRepository.save(paymentOrder);

        reversalRecord.setEscrowScoin(nonNegative(originalResolution.getEscrowScoin()));
        reversalRecord.setMenteeRefundScoin(menteeDebit);
        reversalRecord.setMentorSettlementScoin(mentorDebit);
        reversalRecord.setPlatformSettlementScoin(platformDebit);
        reversalRecord.setSettlementAppliedAtUtc(timeProvider.instant());
    }

    /**
     * Releases one immutable course-session allocation. The allocation UUID, not the course or
     * enrollment UUID, is the ledger source so repeated scheduler runs cannot merge sessions.
     */
    @Transactional
    public void releaseCourseAllocation(UUID mentorUserId,
                                        UUID allocationId,
                                        int mentorPayoutScoin,
                                        int platformRevenueScoin,
                                        int basePriceScoin,
                                        int buyerFeeScoin,
                                        int mentorCommissionScoin,
                                        String operationKey) {
        if (mentorUserId == null || allocationId == null || operationKey == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Course settlement release thiếu định danh bắt buộc");
        }
        SettlementAccount mentorAccount = lockMentorAccount(mentorUserId);
        SettlementAccount platformAccount = lockPlatformAccount();
        if (settlementEntryRepository.findFirstByAccountIdAndSourceTypeAndSourceIdAndEntryTypeOrderByCreatedAtDesc(
                mentorAccount.getId(), LedgerSourceType.COURSE_ENROLLMENT, allocationId, SettlementEntryType.RELEASE
        ).isPresent()) {
            return;
        }
        int mentorPayout = Math.max(0, mentorPayoutScoin);
        int platformRevenue = Math.max(0, platformRevenueScoin);
        // Commission is part of the base price, while the buyer fee is charged on top.
        int gross = Math.addExact(Math.max(0, basePriceScoin), Math.max(0, buyerFeeScoin));
        settlementAccountRepository.addBalance(mentorAccount.getId(), java.math.BigDecimal.valueOf(mentorPayout));
        settlementEntryRepository.save(SettlementEntry.builder()
                .accountId(mentorAccount.getId())
                .entryType(SettlementEntryType.RELEASE)
                .sourceType(LedgerSourceType.COURSE_ENROLLMENT)
                .sourceId(allocationId)
                .amountScoin(mentorPayout)
                .balanceEffectScoin(mentorPayout)
                .grossScoin(gross)
                .commissionScoin(Math.max(0, mentorCommissionScoin))
                .mentorNetScoin(mentorPayout)
                .memo("Course session allocation release " + operationKey)
                .build());
        settlementAccountRepository.addBalance(platformAccount.getId(), java.math.BigDecimal.valueOf(platformRevenue));
        settlementEntryRepository.save(SettlementEntry.builder()
                .accountId(platformAccount.getId())
                .entryType(SettlementEntryType.COMMISSION)
                .sourceType(LedgerSourceType.COURSE_ENROLLMENT)
                .sourceId(allocationId)
                .amountScoin(platformRevenue)
                .balanceEffectScoin(platformRevenue)
                .grossScoin(gross)
                .commissionScoin(Math.max(0, mentorCommissionScoin))
                .mentorNetScoin(mentorPayout)
                .memo("Course platform revenue " + operationKey)
                .build());
    }

    /** Full refund used only for the resolved mentor no-show path. */
    @Retryable(value = ObjectOptimisticLockingFailureException.class, maxAttempts = 3, backoff = @Backoff(delay = 100))
    @Transactional
    public void refundForMentorNoShow(Booking booking) {
        if (booking == null || booking.getId() == null || booking.getMentee() == null) {
            return;
        }
        PaymentOrder paymentOrder = paymentOrderRepository.findByTargetTypeAndTargetIdForUpdate(PaymentTargetType.BOOKING, booking.getId()).orElse(null);
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
            paymentOrder.setRefundedAtUtc(timeProvider.instant());
            paymentOrder.setRefundedAt(timeProvider.nowBusiness());
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
        paymentOrder.setRefundedAtUtc(timeProvider.instant());
        paymentOrder.setRefundedAt(timeProvider.nowBusiness());
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
            paymentOrder.setSettlementStatus(PaymentSettlementStatus.REFUNDED);
            paymentOrder.setRefundedAtUtc(timeProvider.instant());
            paymentOrder.setRefundedAt(timeProvider.nowBusiness());
            paymentOrder.setRefundedScoin(grossScoin);
            paymentOrder.setRefundReason("EARLY_MENTEE_CANCELLATION");
            paymentOrderRepository.save(paymentOrder);
            return;
        }

        int lateRefundBps = paymentProperties.getLateMenteeCancellationRefundBps();
        int lateMentorBps = paymentProperties.getLateMenteeCancellationMentorBps();
        int latePlatformBps = paymentProperties.getLateMenteeCancellationPlatformBps();
        int refundShare = PricingPolicy.bpsAmount(grossScoin, lateRefundBps);
        int mentorShare = PricingPolicy.bpsAmount(grossScoin, lateMentorBps);
        // Platform absorbs the integer rounding remainder so the allocation always equals grossScoin.
        int platformShare = Math.max(0, grossScoin - refundShare - mentorShare);

        if (refundShare > 0) {
            creditLedgerService.refundCredit(
                    booking.getMentee().getId(),
                    CreditOriginType.REFUND,
                    LedgerSourceType.BOOKING,
                    booking.getId(),
                    refundShare,
                    "Refund for late mentee cancellation of booking " + booking.getId()
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
                    .commissionRateBps(latePlatformBps)
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
                    .commissionRateBps(latePlatformBps)
                    .commissionScoin(platformShare)
                    .mentorNetScoin(mentorShare)
                    .memo("Platform commission from late mentee cancellation of booking " + booking.getId())
                    .build());
        }
        paymentOrder.setSettlementStatus(PaymentSettlementStatus.PARTIALLY_SETTLED);
        paymentOrder.setRefundedAtUtc(timeProvider.instant());
        paymentOrder.setRefundedAt(timeProvider.nowBusiness());
        paymentOrder.setRefundedScoin(refundShare);
        paymentOrder.setRefundReason("LATE_MENTEE_CANCELLATION");
        paymentOrderRepository.save(paymentOrder);
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
        paymentOrder.setSettlementStatus(PaymentSettlementStatus.REFUNDED);
        paymentOrder.setRefundedAtUtc(timeProvider.instant());
        paymentOrder.setRefundedAt(timeProvider.nowBusiness());
        paymentOrder.setRefundedScoin(grossScoin);
        paymentOrder.setRefundReason("MENTOR_CANCELLATION");
        paymentOrderRepository.save(paymentOrder);
    }

    @Transactional(readOnly = true)
    public int getMentorAvailableSettlement(UUID mentorUserId) {
        SettlementAccount account = ensureMentorAccount(mentorUserId);
        return settlementBalance(account);
    }

    @Transactional(readOnly = true)
    public java.util.List<SettlementEntry> getRecentTransactions(UUID mentorUserId) {
        SettlementAccount account = ensureMentorAccount(mentorUserId);
        return settlementEntryRepository.findTop15ByAccountIdOrderByCreatedAtDesc(account.getId());
    }

    @Retryable(value = ObjectOptimisticLockingFailureException.class, maxAttempts = 3, backoff = @Backoff(delay = 100))
    @Transactional
    public SettlementEntry holdPayout(UUID mentorUserId, UUID payoutRequestId, int amountScoin, String memo) {
        if (payoutRequestId == null || amountScoin <= 0) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "amountScoin phải lớn hơn 0");
        }
        SettlementAccount account = lockMentorAccount(mentorUserId);
        if (settlementEntryRepository.findFirstByAccountIdAndSourceTypeAndSourceIdAndEntryTypeOrderByCreatedAtDesc(
                account.getId(), LedgerSourceType.PAYOUT_REQUEST, payoutRequestId, SettlementEntryType.HOLD
        ).isPresent()) {
            return settlementEntryRepository.findFirstByAccountIdAndSourceTypeAndSourceIdAndEntryTypeOrderByCreatedAtDesc(
                    account.getId(), LedgerSourceType.PAYOUT_REQUEST, payoutRequestId, SettlementEntryType.HOLD
            ).orElseThrow();
        }

        int rows = settlementAccountRepository.deductBalanceSafely(account.getId(), BigDecimal.valueOf(amountScoin));
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
        if (!settlementAccountRepository.existsByOwnerTypeAndOwnerId(ownerType, ownerId)) {
            ensureAccount(ownerType, ownerId, accountCode);
        }
        return settlementAccountRepository.findByOwnerTypeAndOwnerIdForUpdate(ownerType, ownerId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không thể khóa settlement account"));
    }

    private int settlementBalance(SettlementAccount account) {
        BigDecimal balance = account.getBalance() == null ? BigDecimal.ZERO : account.getBalance();
        try {
            return balance.intValueExact();
        } catch (ArithmeticException exception) {
            throw new BaseException(ErrorCode.DATABASE_ERROR, "Settlement balance không hợp lệ", exception);
        }
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
