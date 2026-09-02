package com.fptu.exe.skillswap.modules.booking.service;

import com.fptu.exe.skillswap.modules.booking.domain.Booking;
import com.fptu.exe.skillswap.modules.booking.domain.BookingStatus;
import com.fptu.exe.skillswap.modules.booking.domain.MeetingPlatform;
import com.fptu.exe.skillswap.modules.booking.domain.MentorAvailabilitySlot;
import com.fptu.exe.skillswap.modules.booking.dto.request.AcceptBookingRequest;
import com.fptu.exe.skillswap.modules.booking.dto.request.CancelBookingRequest;
import com.fptu.exe.skillswap.modules.booking.repository.BookingRepository;
import com.fptu.exe.skillswap.modules.booking.repository.MentorAvailabilitySlotRepository;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.port.UserLockPort;
import com.fptu.exe.skillswap.modules.identity.port.UserQueryPort;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorProfile;
import com.fptu.exe.skillswap.modules.payment.service.PaymentOrderService;
import com.fptu.exe.skillswap.shared.util.DateTimeUtil;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
class BookingCommandLockOrderTest {

    @Mock BookingRepository bookingRepository;
    @Mock MentorAvailabilitySlotRepository slotRepository;
    @Mock UserLockPort userLockPort;
    @Mock UserQueryPort userQueryPort;
    @Mock EntityManager entityManager;
    @Mock SessionService sessionService;
    @Mock ApplicationEventPublisher eventPublisher;
    @Mock BookingResponseMapper responseMapper;
    @Mock PaymentOrderService paymentOrderService;
    @Mock com.fptu.exe.skillswap.modules.identity.port.GoogleCalendarConnectionPort googleCalendarConnectionPort;
    @Mock com.fptu.exe.skillswap.modules.booking.service.meeting.MeetingProviderFactory meetingProviderFactory;

    private UUID mentorId;
    private UUID menteeId;
    private UUID bookingId;
    private MentorProfile mentorProfile;
    private MentorAvailabilitySlot slot;
    private Booking booking;

    @BeforeEach
    void setUp() {
        mentorId = UUID.randomUUID();
        menteeId = UUID.randomUUID();
        bookingId = UUID.randomUUID();
        User mentor = User.builder().id(mentorId).email("mentor-lock@test.com").fullName("Mentor").build();
        User mentee = User.builder().id(menteeId).email("mentee-lock@test.com").fullName("Mentee").build();
        mentorProfile = MentorProfile.builder().userId(mentorId).build();
        slot = MentorAvailabilitySlot.builder()
                .id(UUID.randomUUID()).mentorUserId(mentorId).isActive(true).isBooked(true)
                .startTime(DateTimeUtil.now().plusDays(1)).endTime(DateTimeUtil.now().plusDays(1).plusHours(1))
                .build();
        booking = Booking.builder()
                .id(bookingId).menteeUserId(mentee.getId()).mentorUserId(mentorId).slot(slot)
                .status(BookingStatus.ACCEPTED_AWAITING_PAYMENT)
                .selectedStartTime(slot.getStartTime()).selectedEndTime(slot.getEndTime())
                .serviceIsFreeSnapshot(false).servicePriceScoinSnapshot(30_000)
                .learningGoalTitle("Lock order").build();
    }

    @Test
    void accept_locksBookingBeforeUsersMentorAndSlot() {
        booking.setAcceptedAt(DateTimeUtil.now()); // idempotent return after lock acquisition
        when(bookingRepository.findByIdForMentorDecision(bookingId)).thenReturn(Optional.of(booking));
        when(userLockPort.lockUsersForUpdate(any())).thenReturn(List.of(User.builder().id(booking.getMenteeUserId()).build(),
                User.builder().id(mentorProfile.getUserId()).build()));
        when(slotRepository.findByIdForUpdate(slot.getId())).thenReturn(Optional.of(slot));

        BookingDecisionService service = new BookingDecisionService(
                bookingRepository, slotRepository, userLockPort, userQueryPort,
                sessionService, eventPublisher, responseMapper,
                googleCalendarConnectionPort, meetingProviderFactory);
        service.acceptBooking(mentorId, bookingId,
                new AcceptBookingRequest("retry", MeetingPlatform.GOOGLE_MEET, "https://meet.google.com/test-abc", null));

        InOrder order = inOrder(bookingRepository, userLockPort, slotRepository);
        order.verify(bookingRepository).findByIdForMentorDecision(bookingId);
        order.verify(userLockPort).lockUsersForUpdate(any());
        order.verify(slotRepository).findByIdForUpdate(slot.getId());
    }

    @Test
    void mentorCancel_locksBookingBeforeMentorAndSlot() {
        when(bookingRepository.findByIdForCancellation(bookingId)).thenReturn(Optional.of(booking));
        when(slotRepository.findByIdForUpdate(slot.getId())).thenReturn(Optional.of(slot));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BookingCancellationService service = new BookingCancellationService(
                bookingRepository, slotRepository, userQueryPort,
                sessionService, paymentOrderService, eventPublisher, responseMapper);
        service.cancelBookingByMentor(mentorId, bookingId, new CancelBookingRequest("Emergency"));

        InOrder order = inOrder(bookingRepository, slotRepository);
        order.verify(bookingRepository).findByIdForCancellation(bookingId);
        order.verify(slotRepository).findByIdForUpdate(slot.getId());
    }

}
