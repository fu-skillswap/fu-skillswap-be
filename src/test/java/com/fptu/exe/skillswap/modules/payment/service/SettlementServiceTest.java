package com.fptu.exe.skillswap.modules.payment.service;

import com.fptu.exe.skillswap.infrastructure.config.PaymentProperties;
import com.fptu.exe.skillswap.modules.booking.port.BookingCancellationContext;
import com.fptu.exe.skillswap.modules.booking.port.BookingIssueResolutionSettlementUpdate;
import com.fptu.exe.skillswap.modules.booking.port.BookingIssueResolutionSnapshot;
import com.fptu.exe.skillswap.modules.booking.port.BookingPaymentQueryPort;
import com.fptu.exe.skillswap.modules.booking.port.BookingPaymentSettlementPort;
import com.fptu.exe.skillswap.modules.booking.port.BookingSettlementSnapshot;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorProfile;
import com.fptu.exe.skillswap.modules.payment.domain.CreditLedgerAccount;
import com.fptu.exe.skillswap.modules.payment.domain.CreditOriginType;
import com.fptu.exe.skillswap.modules.payment.domain.LedgerAccountType;
import com.fptu.exe.skillswap.modules.payment.domain.LedgerEntryType;
import com.fptu.exe.skillswap.modules.payment.domain.LedgerSourceType;
import com.fptu.exe.skillswap.modules.payment.domain.PaymentOrder;
import com.fptu.exe.skillswap.modules.payment.domain.PaymentOrderStatus;
import com.fptu.exe.skillswap.modules.payment.domain.SettlementAccount;
import com.fptu.exe.skillswap.modules.payment.domain.SettlementEntry;
import com.fptu.exe.skillswap.modules.payment.domain.SettlementEntryType;
import com.fptu.exe.skillswap.modules.payment.repository.CreditLedgerEntryRepository;
import com.fptu.exe.skillswap.modules.payment.repository.PaymentOrderRepository;
import com.fptu.exe.skillswap.modules.payment.repository.SettlementAccountRepository;
import com.fptu.exe.skillswap.modules.payment.repository.SettlementEntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SettlementServiceTest {

    @Mock
    private SettlementAccountRepository settlementAccountRepository;

    @Mock
    private SettlementEntryRepository settlementEntryRepository;

    @Mock
    private CreditLedgerEntryRepository creditLedgerEntryRepository;

    @Mock
    private PaymentOrderRepository paymentOrderRepository;

    @Mock
    private CreditLedgerService creditLedgerService;

    @Mock
    private BookingPaymentQueryPort bookingPaymentQueryPort;

    @Mock
    private BookingPaymentSettlementPort bookingPaymentSettlementPort;

    private SettlementService settlementService;
    private BookingCancellationContext booking;
    private PaymentOrder paymentOrder;
    private UUID menteeId;
    private UUID mentorId;
    private UUID bookingId;
    private SettlementAccount mentorAccount;
    private SettlementAccount platformAccount;
    private CreditLedgerAccount creditAccount;

    @BeforeEach
    void setUp() {
        settlementService = new SettlementService(
                settlementAccountRepository,
                settlementEntryRepository,
                creditLedgerEntryRepository,
                paymentOrderRepository,
                new PaymentProperties(),
                creditLedgerService,
                bookingPaymentQueryPort,
                bookingPaymentSettlementPort
        );

        menteeId = UUID.randomUUID();
        mentorId = UUID.randomUUID();
        bookingId = UUID.randomUUID();

        booking = new BookingCancellationContext(
                bookingId, menteeId, mentorId, "CANCELLED_BY_MENTEE", null,
                null, null, null, false, true);

        paymentOrder = PaymentOrder.builder()
                .id(UUID.randomUUID())
                .targetType(com.fptu.exe.skillswap.modules.payment.domain.PaymentTargetType.BOOKING).targetId(bookingId)
                .payerUserId(menteeId)
                .mentorUserId(mentorId)
                .grossScoin(100)
                .status(PaymentOrderStatus.PAID)
                .build();

        mentorAccount = SettlementAccount.builder()
                .id(UUID.randomUUID())
                .ownerType(LedgerAccountType.MENTOR_SETTLEMENT)
                .ownerId(mentorId)
                .accountCode("MENTOR-" + mentorId)
                .build();

        platformAccount = SettlementAccount.builder()
                .id(UUID.randomUUID())
                .ownerType(LedgerAccountType.PLATFORM_SETTLEMENT)
                .ownerId(new UUID(0L, 1L))
                .accountCode("PLATFORM")
                .build();

        creditAccount = CreditLedgerAccount.builder()
                .id(UUID.randomUUID())
                .ownerType(LedgerAccountType.USER_CREDIT)
                .ownerId(menteeId)
                .accountCode("CREDIT-" + menteeId)
                .build();

        when(creditLedgerService.getUserAccountForUpdate(menteeId)).thenReturn(creditAccount);
        when(creditLedgerEntryRepository.findFirstByAccountIdAndSourceTypeAndSourceIdAndEntryTypeOrderByCreatedAtDesc(
                eq(creditAccount.getId()),
                eq(LedgerSourceType.BOOKING),
                eq(bookingId),
                eq(LedgerEntryType.REFUND)
        )).thenReturn(Optional.empty());
    }

    @Test
    void handlePaidBookingCancelledByMentee_beforeSixHours_shouldRefundFullAmount() {
        settlementService.handlePaidBookingCancelledByMentee(booking, paymentOrder, false);

        verify(creditLedgerService).refundCredit(
                eq(menteeId),
                eq(CreditOriginType.REFUND),
                eq(LedgerSourceType.BOOKING),
                eq(bookingId),
                eq(100),
                any()
        );
        verify(settlementEntryRepository, never()).save(any(SettlementEntry.class));
    }

    @Test
    void handlePaidBookingCancelledByMentee_withinFourHours_shouldSplitFiftyThirtyFiveFifteen() {
        UUID platformOwnerId = new UUID(0L, 1L);
        when(settlementAccountRepository.existsByOwnerTypeAndOwnerId(LedgerAccountType.MENTOR_SETTLEMENT, mentorId))
                .thenReturn(true);
        when(settlementAccountRepository.existsByOwnerTypeAndOwnerId(LedgerAccountType.PLATFORM_SETTLEMENT, platformOwnerId))
                .thenReturn(true);
        when(settlementAccountRepository.findByOwnerTypeAndOwnerIdForUpdate(LedgerAccountType.MENTOR_SETTLEMENT, mentorId))
                .thenReturn(Optional.of(mentorAccount));
        when(settlementAccountRepository.findByOwnerTypeAndOwnerIdForUpdate(LedgerAccountType.PLATFORM_SETTLEMENT, platformOwnerId))
                .thenReturn(Optional.of(platformAccount));

        settlementService.handlePaidBookingCancelledByMentee(booking, paymentOrder, true);

        verify(creditLedgerService).refundCredit(
                eq(menteeId),
                eq(CreditOriginType.REFUND),
                eq(LedgerSourceType.BOOKING),
                eq(bookingId),
                eq(50),
                any()
        );

        ArgumentCaptor<SettlementEntry> entryCaptor = ArgumentCaptor.forClass(SettlementEntry.class);
        verify(settlementEntryRepository, times(2)).save(entryCaptor.capture());

        var entries = entryCaptor.getAllValues();
        assertEquals(2, entries.size());
        assertTrue(entries.stream().anyMatch(entry ->
                entry.getAccountId().equals(mentorAccount.getId())
                        && entry.getEntryType() == SettlementEntryType.RELEASE
                        && entry.getAmountScoin() == 35));
        assertTrue(entries.stream().anyMatch(entry ->
                entry.getAccountId().equals(platformAccount.getId())
                        && entry.getEntryType() == SettlementEntryType.COMMISSION
                        && entry.getAmountScoin() == 15));
    }

    @Test
    void handlePaidBookingCancelledByMentor_shouldRefundFullAmountToMentee() {
        settlementService.handlePaidBookingCancelledByMentor(booking, paymentOrder);

        verify(creditLedgerService).refundCredit(
                eq(menteeId),
                eq(CreditOriginType.REFUND),
                eq(LedgerSourceType.BOOKING),
                eq(bookingId),
                eq(100),
                any()
        );
        verify(settlementEntryRepository, never()).save(any(SettlementEntry.class));
    }

    @Test
    void applyAdminIssueResolution_freeBooking_shouldPersistAppliedSettlementState() {
        UUID resolutionId = UUID.randomUUID();
        BookingIssueResolutionSnapshot resolution = resolution(resolutionId, "RELEASE_AS_IS");
        when(bookingPaymentSettlementPort.findSettlementSnapshot(bookingId))
                .thenReturn(Optional.of(settlementSnapshot()));
        when(bookingPaymentSettlementPort.findIssueResolution(bookingId, resolutionId))
                .thenReturn(Optional.of(resolution));

        settlementService.applyAdminIssueResolution(bookingId, resolutionId);

        ArgumentCaptor<BookingIssueResolutionSettlementUpdate> updateCaptor =
                ArgumentCaptor.forClass(BookingIssueResolutionSettlementUpdate.class);
        verify(bookingPaymentSettlementPort).updateIssueResolutionSettlement(
                eq(bookingId), eq(resolutionId), updateCaptor.capture());
        BookingIssueResolutionSettlementUpdate update = updateCaptor.getValue();
        assertEquals("APPLIED", update.status());
        assertEquals(0, update.escrowScoin());
        assertEquals(0, update.menteeRefundScoin());
        assertEquals(0, update.mentorSettlementScoin());
        assertEquals(0, update.platformSettlementScoin());
        assertTrue(update.settlementAppliedAtUtc() != null);
    }

    @Test
    void applyReversal_freeBooking_shouldPersistAppliedReversalState() {
        UUID originalId = UUID.randomUUID();
        UUID reversalId = UUID.randomUUID();
        when(bookingPaymentSettlementPort.findIssueResolution(bookingId, originalId))
                .thenReturn(Optional.of(resolution(originalId, "RELEASE_AS_IS")));
        when(bookingPaymentSettlementPort.findIssueResolution(bookingId, reversalId))
                .thenReturn(Optional.of(resolution(reversalId, "RELEASE_AS_IS")));

        settlementService.applyReversal(bookingId, originalId, reversalId);

        ArgumentCaptor<BookingIssueResolutionSettlementUpdate> updateCaptor =
                ArgumentCaptor.forClass(BookingIssueResolutionSettlementUpdate.class);
        verify(bookingPaymentSettlementPort).updateIssueResolutionSettlement(
                eq(bookingId), eq(reversalId), updateCaptor.capture());
        BookingIssueResolutionSettlementUpdate update = updateCaptor.getValue();
        assertEquals("APPLIED", update.status());
        assertEquals(0, update.escrowScoin());
        assertTrue(update.settlementAppliedAtUtc() != null);
    }

    @Test
    void applyReversal_insufficientMentorBalance_shouldPersistManualFinanceReview() {
        UUID originalId = UUID.randomUUID();
        UUID reversalId = UUID.randomUUID();
        PaymentOrder heldOrder = PaymentOrder.builder()
                .id(UUID.randomUUID())
                .targetType(com.fptu.exe.skillswap.modules.payment.domain.PaymentTargetType.BOOKING)
                .targetId(bookingId)
                .payerUserId(menteeId)
                .mentorUserId(mentorId)
                .grossScoin(100)
                .status(PaymentOrderStatus.PAID)
                .build();
        when(bookingPaymentSettlementPort.findIssueResolution(bookingId, originalId))
                .thenReturn(Optional.of(new BookingIssueResolutionSnapshot(
                        originalId, "PARTIAL_SETTLEMENT", null, "APPLIED", 50, 40, 10,
                        100, 50, 40, 10, null, null)));
        when(bookingPaymentSettlementPort.findIssueResolution(bookingId, reversalId))
                .thenReturn(Optional.of(resolution(reversalId, "PARTIAL_SETTLEMENT")));
        when(paymentOrderRepository.findByTargetTypeAndTargetIdForUpdate(
                com.fptu.exe.skillswap.modules.payment.domain.PaymentTargetType.BOOKING, bookingId))
                .thenReturn(Optional.of(heldOrder));
        when(settlementAccountRepository.existsByOwnerTypeAndOwnerId(LedgerAccountType.MENTOR_SETTLEMENT, mentorId))
                .thenReturn(true);
        when(settlementAccountRepository.findByOwnerTypeAndOwnerIdForUpdate(LedgerAccountType.MENTOR_SETTLEMENT, mentorId))
                .thenReturn(Optional.of(mentorAccount));

        settlementService.applyReversal(bookingId, originalId, reversalId);

        ArgumentCaptor<BookingIssueResolutionSettlementUpdate> updateCaptor =
                ArgumentCaptor.forClass(BookingIssueResolutionSettlementUpdate.class);
        verify(bookingPaymentSettlementPort).updateIssueResolutionSettlement(
                eq(bookingId), eq(reversalId), updateCaptor.capture());
        BookingIssueResolutionSettlementUpdate update = updateCaptor.getValue();
        assertEquals("MANUAL_FINANCE_REVIEW", update.status());
        assertEquals(100, update.escrowScoin());
        assertEquals(50, update.menteeRefundScoin());
        assertEquals(40, update.mentorSettlementScoin());
        assertEquals(10, update.platformSettlementScoin());
        verify(settlementEntryRepository, never()).save(any(SettlementEntry.class));
    }

    private BookingSettlementSnapshot settlementSnapshot() {
        return new BookingSettlementSnapshot(
                bookingId, menteeId, mentorId, 100, "COMPLETED", "USER_CONFIRMED",
                null, true, null, null);
    }

    private BookingIssueResolutionSnapshot resolution(UUID resolutionId, String action) {
        return new BookingIssueResolutionSnapshot(
                resolutionId, action, null, "APPLIED", null, null, null,
                null, null, null, null, null, null);
    }
}
