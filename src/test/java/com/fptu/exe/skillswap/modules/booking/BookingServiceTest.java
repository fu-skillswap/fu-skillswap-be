package com.fptu.exe.skillswap.modules.booking;

import com.fptu.exe.skillswap.modules.booking.domain.Booking;
import com.fptu.exe.skillswap.modules.booking.domain.BookingStatus;
import com.fptu.exe.skillswap.modules.booking.domain.MentorAvailabilitySlot;
import com.fptu.exe.skillswap.modules.booking.domain.Session;
import com.fptu.exe.skillswap.modules.booking.domain.SessionStatus;
import com.fptu.exe.skillswap.modules.booking.dto.AcceptBookingRequest;
import com.fptu.exe.skillswap.modules.booking.dto.BookingResponse;
import com.fptu.exe.skillswap.modules.booking.dto.BookingListRequest;
import com.fptu.exe.skillswap.modules.booking.dto.BookingViewRole;
import com.fptu.exe.skillswap.modules.booking.dto.CreateBookingRequest;
import com.fptu.exe.skillswap.modules.booking.dto.RejectBookingRequest;
import com.fptu.exe.skillswap.modules.booking.repository.BookingRepository;
import com.fptu.exe.skillswap.modules.booking.repository.MentorAvailabilitySlotRepository;
import com.fptu.exe.skillswap.modules.booking.repository.SessionRepository;
import com.fptu.exe.skillswap.modules.booking.service.BookingService;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.domain.UserStatus;
import com.fptu.exe.skillswap.modules.identity.repository.UserRepository;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorProfile;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorStatus;
import com.fptu.exe.skillswap.modules.mentor.domain.TeachingMode;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorServiceRepository;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private MentorAvailabilitySlotRepository mentorAvailabilitySlotRepository;

    @Mock
    private MentorServiceRepository mentorServiceRepository;

    @Mock
    private SessionRepository sessionRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private BookingService bookingService;

    private UUID menteeUserId;
    private UUID mentorUserId;
    private UUID slotId;
    private User mentee;
    private MentorProfile mentorProfile;
    private MentorAvailabilitySlot slot;

    @BeforeEach
    void setUp() {
        menteeUserId = UUID.randomUUID();
        mentorUserId = UUID.randomUUID();
        slotId = UUID.randomUUID();

        mentee = User.builder()
                .id(menteeUserId)
                .email("mentee@fpt.edu.vn")
                .fullName("Mentee")
                .status(UserStatus.ACTIVE)
                .build();

        mentorProfile = MentorProfile.builder()
                .userId(mentorUserId)
                .user(User.builder()
                        .id(mentorUserId)
                        .email("mentor@fpt.edu.vn")
                        .fullName("Mentor One")
                        .build())
                .status(MentorStatus.ACTIVE)
                .verifiedAt(LocalDateTime.now().minusDays(3))
                .teachingMode(TeachingMode.ONLINE)
                .sessionDuration(60)
                .hourlyRate(new BigDecimal("150000"))
                .yearsOfExperience(new BigDecimal("3.5"))
                .isAvailable(true)
                .build();

        slot = MentorAvailabilitySlot.builder()
                .id(slotId)
                .mentorProfile(mentorProfile)
                .startTime(LocalDateTime.now().plusDays(1).withHour(9).withMinute(0))
                .endTime(LocalDateTime.now().plusDays(1).withHour(10).withMinute(0))
                .timezone("Asia/Ho_Chi_Minh")
                .isActive(true)
                .isBooked(false)
                .build();
    }

    @Test
    void createBooking_availableSlot_shouldMarkBookedAndPersistBooking() {
        CreateBookingRequest request = new CreateBookingRequest(
                mentorUserId,
                slotId,
                null,
                "Cần định hướng backend Java",
                "Muốn nhờ mentor review CV"
        );

        when(userRepository.findById(menteeUserId)).thenReturn(Optional.of(mentee));
        when(mentorAvailabilitySlotRepository.findByIdForUpdate(slotId)).thenReturn(Optional.of(slot));
        when(bookingRepository.save(any())).thenAnswer(invocation -> {
            Booking booking = invocation.getArgument(0);
            booking.setId(UUID.randomUUID());
            return booking;
        });

        BookingResponse response = bookingService.createBooking(menteeUserId, request);

        assertThat(response.mentorUserId()).isEqualTo(mentorUserId);
        assertThat(response.slotId()).isEqualTo(slotId);
        assertThat(response.status()).isNotNull();
        assertThat(slot.isBooked()).isTrue();

        ArgumentCaptor<Booking> bookingCaptor = ArgumentCaptor.forClass(Booking.class);
        verify(bookingRepository).save(bookingCaptor.capture());
        assertThat(bookingCaptor.getValue().getRequestedStartTime()).isEqualTo(slot.getStartTime());
        assertThat(bookingCaptor.getValue().getRequestedEndTime()).isEqualTo(slot.getEndTime());
    }

    @Test
    void createBooking_bookedSlot_shouldThrowConflict() {
        CreateBookingRequest request = new CreateBookingRequest(
                mentorUserId,
                slotId,
                null,
                "Cần định hướng backend Java",
                "Muốn nhờ mentor review CV"
        );
        slot.setBooked(true);

        when(userRepository.findById(menteeUserId)).thenReturn(Optional.of(mentee));
        when(mentorAvailabilitySlotRepository.findByIdForUpdate(slotId)).thenReturn(Optional.of(slot));

        assertThatThrownBy(() -> bookingService.createBooking(menteeUserId, request))
                .isInstanceOf(BaseException.class)
                .extracting(ex -> ((BaseException) ex).getErrorCode())
                .isEqualTo(ErrorCode.RESOURCE_CONFLICT);
    }

    @Test
    void createBooking_selfBooking_shouldThrowConflict() {
        CreateBookingRequest request = new CreateBookingRequest(
                mentorUserId,
                slotId,
                null,
                "Cần định hướng backend Java",
                "Muốn nhờ mentor review CV"
        );
        mentee.setId(mentorUserId);

        when(userRepository.findById(mentorUserId)).thenReturn(Optional.of(mentee));
        when(mentorAvailabilitySlotRepository.findByIdForUpdate(slotId)).thenReturn(Optional.of(slot));

        assertThatThrownBy(() -> bookingService.createBooking(mentorUserId, request))
                .isInstanceOf(BaseException.class)
                .extracting(ex -> ((BaseException) ex).getErrorCode())
                .isEqualTo(ErrorCode.RESOURCE_CONFLICT);
    }

    @Test
    void createBooking_inactiveUser_shouldThrowForbidden() {
        CreateBookingRequest request = new CreateBookingRequest(
                mentorUserId,
                slotId,
                null,
                "Cần định hướng backend Java",
                "Muốn nhờ mentor review CV"
        );
        mentee.setStatus(UserStatus.INACTIVE);

        when(userRepository.findById(menteeUserId)).thenReturn(Optional.of(mentee));

        assertThatThrownBy(() -> bookingService.createBooking(menteeUserId, request))
                .isInstanceOf(BaseException.class)
                .extracting(ex -> ((BaseException) ex).getErrorCode())
                .isEqualTo(ErrorCode.USER_INACTIVE);
    }

    @Test
    void getMyBookings_asMentee_shouldReturnPagedBookings() {
        Booking booking = Booking.builder()
                .id(UUID.randomUUID())
                .mentee(mentee)
                .mentorProfile(mentorProfile)
                .slot(slot)
                .status(BookingStatus.PENDING)
                .learningGoalTitle("Cần định hướng backend Java")
                .learningGoalDescription("Muốn nhờ mentor review CV")
                .requestedStartTime(slot.getStartTime())
                .requestedEndTime(slot.getEndTime())
                .build();

        BookingListRequest request = new BookingListRequest();
        request.setRole(BookingViewRole.MENTEE);

        when(bookingRepository.findByMenteeId(eq(menteeUserId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(booking)));
        when(sessionRepository.findByBookingIdIn(List.of(booking.getId()))).thenReturn(List.of());

        assertThat(bookingService.getMyBookings(menteeUserId, request).getContent()).hasSize(1);
        assertThat(bookingService.getMyBookings(menteeUserId, request).getContent().get(0).mentorDisplayName())
                .isEqualTo("Mentor One");
    }

    @Test
    void getBookingDetail_notOwner_shouldThrowForbidden() {
        UUID strangerId = UUID.randomUUID();
        Booking booking = Booking.builder()
                .id(UUID.randomUUID())
                .mentee(mentee)
                .mentorProfile(mentorProfile)
                .slot(slot)
                .status(BookingStatus.PENDING)
                .learningGoalTitle("Cần định hướng backend Java")
                .learningGoalDescription("Muốn nhờ mentor review CV")
                .requestedStartTime(slot.getStartTime())
                .requestedEndTime(slot.getEndTime())
                .build();

        when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> bookingService.getBookingDetail(strangerId, booking.getId()))
                .isInstanceOf(BaseException.class)
                .extracting(ex -> ((BaseException) ex).getErrorCode())
                .isEqualTo(ErrorCode.UNAUTHORIZED);
    }

    @Test
    void acceptBooking_pendingBooking_shouldCreateSessionAndMarkAccepted() {
        UUID bookingId = UUID.randomUUID();
        Booking booking = Booking.builder()
                .id(bookingId)
                .mentee(mentee)
                .mentorProfile(mentorProfile)
                .slot(slot)
                .status(BookingStatus.PENDING)
                .learningGoalTitle("Cần định hướng backend Java")
                .requestedStartTime(slot.getStartTime())
                .requestedEndTime(slot.getEndTime())
                .build();

        when(bookingRepository.findByIdForMentorDecision(bookingId)).thenReturn(Optional.of(booking));
        when(sessionRepository.findByBookingId(bookingId)).thenReturn(Optional.empty());
        when(sessionRepository.save(any())).thenAnswer(invocation -> {
            Session session = invocation.getArgument(0);
            session.setId(UUID.randomUUID());
            return session;
        });

        BookingResponse response = bookingService.acceptBooking(mentorUserId, bookingId, new AcceptBookingRequest("Hẹn gặp em đúng giờ nhé"));

        assertThat(response.status()).isEqualTo(BookingStatus.ACCEPTED);
        assertThat(response.sessionId()).isNotNull();
        assertThat(response.sessionStatus()).isEqualTo(SessionStatus.SCHEDULED);
        assertThat(response.mentorResponseNote()).isEqualTo("Hẹn gặp em đúng giờ nhé");
    }

    @Test
    void rejectBooking_pendingBooking_shouldFreeSlotAndMarkRejected() {
        UUID bookingId = UUID.randomUUID();
        slot.setBooked(true);
        Booking booking = Booking.builder()
                .id(bookingId)
                .mentee(mentee)
                .mentorProfile(mentorProfile)
                .slot(slot)
                .status(BookingStatus.PENDING)
                .learningGoalTitle("Cần định hướng backend Java")
                .requestedStartTime(slot.getStartTime())
                .requestedEndTime(slot.getEndTime())
                .build();

        when(bookingRepository.findByIdForMentorDecision(bookingId)).thenReturn(Optional.of(booking));

        BookingResponse response = bookingService.rejectBooking(
                mentorUserId,
                bookingId,
                new RejectBookingRequest("Lịch này mentor không còn phù hợp", "Em chọn giúp anh slot khác nhé")
        );

        assertThat(response.status()).isEqualTo(BookingStatus.REJECTED);
        assertThat(response.rejectReason()).isEqualTo("Lịch này mentor không còn phù hợp");
        assertThat(slot.isBooked()).isFalse();
        verify(sessionRepository, never()).save(any());
    }
}
