package com.fptu.exe.skillswap.modules.payment.service;

import com.fptu.exe.skillswap.infrastructure.config.PaymentProperties;
import com.fptu.exe.skillswap.modules.booking.domain.Booking;
import com.fptu.exe.skillswap.modules.identity.domain.User;
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

    private SettlementService settlementService;
    private Booking booking;
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
                creditLedgerService
        );

        menteeId = UUID.randomUUID();
        mentorId = UUID.randomUUID();
        bookingId = UUID.randomUUID();

        User mentee = new User();
        mentee.setId(menteeId);

        booking = Booking.builder()
                .id(bookingId)
                .mentee(mentee)
                .mentorProfile(MentorProfile.builder().userId(mentorId).build())
                .build();

        paymentOrder = PaymentOrder.builder()
                .id(UUID.randomUUID())
                .bookingId(bookingId)
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
    void handlePaidBookingCancelledByMentee_withinSixHours_shouldSplitFiftyThirtyFiveFifteen() {
        when(settlementAccountRepository.findByOwnerTypeAndOwnerId(LedgerAccountType.MENTOR_SETTLEMENT, mentorId))
                .thenReturn(Optional.of(mentorAccount));
        when(settlementAccountRepository.findByOwnerTypeAndOwnerId(LedgerAccountType.PLATFORM_SETTLEMENT, new UUID(0L, 1L)))
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
}
