package com.fptu.exe.skillswap.modules.booking;

import com.fptu.exe.skillswap.modules.booking.domain.Booking;
import com.fptu.exe.skillswap.modules.booking.domain.BookingStatus;
import com.fptu.exe.skillswap.modules.booking.domain.GroupSession;
import com.fptu.exe.skillswap.modules.booking.domain.GroupSessionRegistrationStatus;
import com.fptu.exe.skillswap.modules.booking.domain.GroupSessionStatus;
import com.fptu.exe.skillswap.modules.booking.dto.request.CreateGroupSessionBookingRequest;
import com.fptu.exe.skillswap.modules.booking.repository.BookingRepository;
import com.fptu.exe.skillswap.modules.booking.repository.GroupSessionRepository;
import com.fptu.exe.skillswap.modules.booking.service.GroupSessionCommerceService;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.repository.UserRepository;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorProfile;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorService;
import com.fptu.exe.skillswap.modules.payment.service.PaymentOrderService;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GroupSessionCommerceServiceTest {

    @Mock private GroupSessionRepository groupSessionRepository;
    @Mock private BookingRepository bookingRepository;
    @Mock private UserRepository userRepository;
    @Mock private PaymentOrderService paymentOrderService;
    @Mock private ApplicationEventPublisher eventPublisher;

    private GroupSessionCommerceService commerceService;
    private UUID learnerId;
    private UUID groupSessionId;
    private GroupSession session;

    @BeforeEach
    void setUp() {
        commerceService = new GroupSessionCommerceService(groupSessionRepository, bookingRepository, userRepository,
                paymentOrderService, eventPublisher);
        learnerId = UUID.randomUUID();
        groupSessionId = UUID.randomUUID();
        User learner = User.builder().id(learnerId).email("learner@example.test").fullName("Learner").build();
        MentorProfile mentor = MentorProfile.builder().userId(UUID.randomUUID()).build();
        MentorService service = MentorService.builder().id(UUID.randomUUID()).mentorProfile(mentor)
                .title("Java group practice").durationMinutes(60).build();
        LocalDateTime start = LocalDateTime.now().plusDays(2).withSecond(0).withNano(0);
        session = GroupSession.builder().id(groupSessionId).mentorProfile(mentor).service(service)
                .status(GroupSessionStatus.OPEN).registrationStatus(GroupSessionRegistrationStatus.OPEN)
                .scheduledStartAt(start).scheduledEndAt(start.plusHours(1)).registrationClosesAt(start.minusHours(1))
                .maxParticipants(10).reservedSeatCount(0).serviceTitleSnapshot("Java group practice")
                .serviceDurationSnapshot(60).serviceIsFreeSnapshot(false).servicePriceScoinSnapshot(100).build();

        when(userRepository.findByIdForUpdate(learnerId)).thenReturn(Optional.of(learner));
        when(groupSessionRepository.findByIdForUpdate(groupSessionId)).thenReturn(Optional.of(session));
        lenient().when(bookingRepository.existsByMenteeIdAndGroupSessionIdAndStatusIn(eq(learnerId), eq(groupSessionId), any())).thenReturn(false);
        lenient().when(bookingRepository.findMenteeOverlappingBookingsForUpdate(eq(learnerId), any(), any(), any())).thenReturn(List.of());
        lenient().when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> {
            Booking booking = invocation.getArgument(0);
            booking.setId(UUID.randomUUID());
            return booking;
        });
    }

    @Test
    void createPaidSeat_holdsCapacityWithoutCreatingPaymentOrder() {
        Booking booking = commerceService.createSeat(learnerId, groupSessionId,
                new CreateGroupSessionBookingRequest("Mock interview", null));

        assertEquals(BookingStatus.ACCEPTED_AWAITING_PAYMENT, booking.getStatus());
        assertEquals(1, session.getReservedSeatCount());
        assertEquals(groupSessionId, booking.getGroupSession().getId());
        assertEquals(session.getScheduledStartAt(), booking.getSelectedStartTime());
        verify(paymentOrderService, never()).handleMentorCancellation(any());
    }

    @Test
    void createFreeSeat_confirmsWithoutPaymentOrder() {
        session.setServiceIsFreeSnapshot(true);
        session.setServicePriceScoinSnapshot(0);

        Booking booking = commerceService.createSeat(learnerId, groupSessionId,
                new CreateGroupSessionBookingRequest("Practice", null));

        assertEquals(BookingStatus.PAID, booking.getStatus());
        assertEquals(1, session.getReservedSeatCount());
    }

    @Test
    void createSeat_rejectsFullSessionWithoutPersistingBooking() {
        session.setReservedSeatCount(session.getMaxParticipants());

        assertThrows(BaseException.class, () -> commerceService.createSeat(learnerId, groupSessionId,
                new CreateGroupSessionBookingRequest("Practice", null)));

        verify(bookingRepository, never()).save(any(Booking.class));
    }
}
