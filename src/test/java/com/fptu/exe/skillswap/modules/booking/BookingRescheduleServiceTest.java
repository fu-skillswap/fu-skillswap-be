package com.fptu.exe.skillswap.modules.booking;

import com.fptu.exe.skillswap.modules.admin.repository.AuditLogRepository;
import com.fptu.exe.skillswap.modules.booking.domain.Booking;
import com.fptu.exe.skillswap.modules.booking.domain.BookingRescheduleActorRole;
import com.fptu.exe.skillswap.modules.booking.domain.BookingRescheduleRequest;
import com.fptu.exe.skillswap.modules.booking.domain.BookingRescheduleStatus;
import com.fptu.exe.skillswap.modules.booking.domain.BookingStatus;
import com.fptu.exe.skillswap.modules.booking.domain.MentorAvailabilitySlot;
import com.fptu.exe.skillswap.modules.booking.dto.request.CreateBookingRescheduleRequest;
import com.fptu.exe.skillswap.modules.booking.dto.request.RespondBookingRescheduleRequest;
import com.fptu.exe.skillswap.modules.booking.dto.response.BookingRescheduleRequestResponse;
import com.fptu.exe.skillswap.modules.booking.repository.BookingRepository;
import com.fptu.exe.skillswap.modules.booking.repository.BookingRescheduleRequestRepository;
import com.fptu.exe.skillswap.modules.booking.repository.MentorAvailabilitySlotRepository;
import com.fptu.exe.skillswap.modules.booking.service.BookingRescheduleService;
import com.fptu.exe.skillswap.modules.booking.service.BookingSlotValidator;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.repository.UserRepository;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorProfile;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorService;
import com.fptu.exe.skillswap.modules.notification.service.NotificationService;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingRescheduleServiceTest {

    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private BookingRescheduleRequestRepository bookingRescheduleRequestRepository;
    @Mock
    private MentorAvailabilitySlotRepository mentorAvailabilitySlotRepository;
    @Mock
    private BookingSlotValidator bookingSlotValidator;
    @Mock
    private NotificationService notificationService;
    @Mock
    private AuditLogRepository auditLogRepository;
    @Mock
    private UserRepository userRepository;

    private BookingRescheduleService bookingRescheduleService;
    private UUID menteeId;
    private UUID mentorId;
    private Booking booking;
    private MentorAvailabilitySlot currentSlot;
    private MentorAvailabilitySlot proposedSlot;

    @BeforeEach
    void setUp() {
        bookingRescheduleService = new BookingRescheduleService(
                bookingRepository,
                bookingRescheduleRequestRepository,
                mentorAvailabilitySlotRepository,
                bookingSlotValidator,
                notificationService,
                auditLogRepository,
                userRepository
        );

        menteeId = UUID.randomUUID();
        mentorId = UUID.randomUUID();

        User mentee = new User();
        mentee.setId(menteeId);

        MentorProfile mentorProfile = MentorProfile.builder()
                .userId(mentorId)
                .build();

        MentorService service = MentorService.builder()
                .id(UUID.randomUUID())
                .durationMinutes(60)
                .build();

        currentSlot = new MentorAvailabilitySlot();
        currentSlot.setId(UUID.randomUUID());
        currentSlot.setMentorProfile(mentorProfile);
        currentSlot.setStartTime(LocalDateTime.now().plusHours(12));
        currentSlot.setEndTime(LocalDateTime.now().plusHours(13));
        currentSlot.setActive(true);

        proposedSlot = new MentorAvailabilitySlot();
        proposedSlot.setId(UUID.randomUUID());
        proposedSlot.setMentorProfile(mentorProfile);
        proposedSlot.setStartTime(LocalDateTime.now().plusHours(15));
        proposedSlot.setEndTime(LocalDateTime.now().plusHours(16));
        proposedSlot.setActive(true);

        booking = Booking.builder()
                .id(UUID.randomUUID())
                .mentee(mentee)
                .mentorProfile(mentorProfile)
                .service(service)
                .slot(currentSlot)
                .status(BookingStatus.ACCEPTED)
                .selectedStartTime(currentSlot.getStartTime())
                .selectedEndTime(currentSlot.getEndTime())
                .rescheduleCount(0)
                .build();
    }

    @Test
    void createByMentee_afterSixHoursWindow_shouldThrowConflict() {
        booking.setSelectedStartTime(LocalDateTime.now().plusHours(5));
        booking.setSelectedEndTime(LocalDateTime.now().plusHours(6));
        when(bookingRepository.findByIdForSessionUpdate(booking.getId())).thenReturn(Optional.of(booking));

        BaseException ex = assertThrows(BaseException.class, () -> bookingRescheduleService.createByMentee(
                menteeId,
                booking.getId(),
                new CreateBookingRescheduleRequest(
                        proposedSlot.getId(),
                        proposedSlot.getStartTime(),
                        proposedSlot.getEndTime(),
                        "Muốn dời lịch"
                )
        ));

        assertEquals(ErrorCode.RESOURCE_CONFLICT, ex.getErrorCode());
    }

    @Test
    void acceptByParticipant_shouldApplyNewSlotAndIncrementQuota() {
        BookingRescheduleRequest request = BookingRescheduleRequest.builder()
                .id(UUID.randomUUID())
                .booking(booking)
                .currentSlot(currentSlot)
                .proposedSlot(proposedSlot)
                .requestedByUserId(menteeId)
                .requesterRole(BookingRescheduleActorRole.MENTEE)
                .status(BookingRescheduleStatus.PENDING)
                .requestReason("Dời lịch")
                .previousSelectedStartTime(currentSlot.getStartTime())
                .previousSelectedEndTime(currentSlot.getEndTime())
                .proposedSelectedStartTime(proposedSlot.getStartTime())
                .proposedSelectedEndTime(proposedSlot.getEndTime())
                .requestedAt(LocalDateTime.now())
                .build();

        when(bookingRescheduleRequestRepository.findByIdForUpdate(request.getId())).thenReturn(Optional.of(request));
        when(mentorAvailabilitySlotRepository.findByIdForUpdate(proposedSlot.getId())).thenReturn(Optional.of(proposedSlot));
        when(mentorAvailabilitySlotRepository.findByIdForUpdate(currentSlot.getId())).thenReturn(Optional.of(currentSlot));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));
        when(bookingRescheduleRequestRepository.save(any(BookingRescheduleRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        BookingRescheduleRequestResponse response = bookingRescheduleService.acceptByParticipant(
                mentorId,
                request.getId(),
                new RespondBookingRescheduleRequest("Ok dời lịch")
        );

        assertEquals("ACCEPTED", response.status());
        assertEquals(proposedSlot.getId(), booking.getSlot().getId());
        assertEquals(1, booking.getRescheduleCount());
        verify(bookingSlotValidator).validateServiceAttachedToSlot(eq(proposedSlot.getId()), eq(booking.getService().getId()));
    }

    @Test
    void acceptByAdmin_shouldWriteAuditLog() {
        UUID adminId = UUID.randomUUID();
        User admin = new User();
        admin.setId(adminId);

        BookingRescheduleRequest request = BookingRescheduleRequest.builder()
                .id(UUID.randomUUID())
                .booking(booking)
                .currentSlot(currentSlot)
                .proposedSlot(proposedSlot)
                .requestedByUserId(menteeId)
                .requesterRole(BookingRescheduleActorRole.MENTEE)
                .status(BookingRescheduleStatus.PENDING)
                .requestReason("Dời lịch")
                .previousSelectedStartTime(currentSlot.getStartTime())
                .previousSelectedEndTime(currentSlot.getEndTime())
                .proposedSelectedStartTime(proposedSlot.getStartTime())
                .proposedSelectedEndTime(proposedSlot.getEndTime())
                .requestedAt(LocalDateTime.now())
                .build();

        when(bookingRescheduleRequestRepository.findByIdForUpdate(request.getId())).thenReturn(Optional.of(request));
        when(mentorAvailabilitySlotRepository.findByIdForUpdate(proposedSlot.getId())).thenReturn(Optional.of(proposedSlot));
        when(mentorAvailabilitySlotRepository.findByIdForUpdate(currentSlot.getId())).thenReturn(Optional.of(currentSlot));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));
        when(bookingRescheduleRequestRepository.save(any(BookingRescheduleRequest.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.findById(adminId)).thenReturn(Optional.of(admin));

        BookingRescheduleRequestResponse response = bookingRescheduleService.acceptByAdmin(
                adminId,
                request.getId(),
                new RespondBookingRescheduleRequest("Manual support approve")
        );

        assertEquals("ACCEPTED", response.status());
        verify(auditLogRepository).save(any());
    }

    @Test
    void expirePendingRequests_shouldExpireAtTwoHoursBeforeStart() {
        booking.setSelectedStartTime(LocalDateTime.now().plusMinutes(100));
        booking.setSelectedEndTime(LocalDateTime.now().plusMinutes(160));

        BookingRescheduleRequest request = BookingRescheduleRequest.builder()
                .id(UUID.randomUUID())
                .booking(booking)
                .currentSlot(currentSlot)
                .proposedSlot(proposedSlot)
                .requestedByUserId(menteeId)
                .requesterRole(BookingRescheduleActorRole.MENTEE)
                .status(BookingRescheduleStatus.PENDING)
                .requestReason("Dời lịch")
                .previousSelectedStartTime(currentSlot.getStartTime())
                .previousSelectedEndTime(currentSlot.getEndTime())
                .proposedSelectedStartTime(proposedSlot.getStartTime())
                .proposedSelectedEndTime(proposedSlot.getEndTime())
                .requestedAt(LocalDateTime.now())
                .build();

        when(bookingRescheduleRequestRepository.findExpirablePendingRequests(eq(BookingRescheduleStatus.PENDING), any()))
                .thenReturn(java.util.List.of(request));
        when(bookingRescheduleRequestRepository.save(any(BookingRescheduleRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        int expired = bookingRescheduleService.expirePendingRequests();

        assertEquals(1, expired);
        assertEquals(BookingRescheduleStatus.EXPIRED, request.getStatus());
    }

    @Test
    void expirePendingRequests_shouldNotExpireBeforeTwoHourThreshold() {
        booking.setSelectedStartTime(LocalDateTime.now().plusHours(3));
        booking.setSelectedEndTime(LocalDateTime.now().plusHours(4));

        BookingRescheduleRequest request = BookingRescheduleRequest.builder()
                .id(UUID.randomUUID())
                .booking(booking)
                .currentSlot(currentSlot)
                .proposedSlot(proposedSlot)
                .requestedByUserId(menteeId)
                .requesterRole(BookingRescheduleActorRole.MENTEE)
                .status(BookingRescheduleStatus.PENDING)
                .requestReason("Dời lịch")
                .previousSelectedStartTime(currentSlot.getStartTime())
                .previousSelectedEndTime(currentSlot.getEndTime())
                .proposedSelectedStartTime(proposedSlot.getStartTime())
                .proposedSelectedEndTime(proposedSlot.getEndTime())
                .requestedAt(LocalDateTime.now())
                .build();

        when(bookingRescheduleRequestRepository.findExpirablePendingRequests(eq(BookingRescheduleStatus.PENDING), any()))
                .thenReturn(java.util.List.of(request));

        int expired = bookingRescheduleService.expirePendingRequests();

        assertEquals(0, expired);
        assertEquals(BookingRescheduleStatus.PENDING, request.getStatus());
        verify(bookingRescheduleRequestRepository, never()).save(any(BookingRescheduleRequest.class));
    }
}
