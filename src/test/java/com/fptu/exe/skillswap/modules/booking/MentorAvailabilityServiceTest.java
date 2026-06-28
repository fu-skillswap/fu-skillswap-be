package com.fptu.exe.skillswap.modules.booking;

import com.fptu.exe.skillswap.modules.booking.domain.AvailabilityRepeatType;
import com.fptu.exe.skillswap.modules.booking.domain.AvailabilityRuleType;
import com.fptu.exe.skillswap.modules.booking.domain.AvailabilitySlotService;
import com.fptu.exe.skillswap.modules.booking.domain.Booking;
import com.fptu.exe.skillswap.modules.booking.domain.BookingStatus;
import com.fptu.exe.skillswap.modules.booking.domain.MentorAvailabilityRule;
import com.fptu.exe.skillswap.modules.booking.domain.MentorAvailabilitySlot;
import com.fptu.exe.skillswap.modules.booking.dto.request.CreateAvailabilitySlotRequest;
import com.fptu.exe.skillswap.modules.booking.dto.request.UpdateAvailabilitySlotRequest;
import com.fptu.exe.skillswap.modules.booking.dto.response.MentorManagedAvailabilitySlotResponse;
import com.fptu.exe.skillswap.modules.booking.repository.AvailabilitySlotServiceRepository;
import com.fptu.exe.skillswap.modules.booking.repository.BookingRepository;
import com.fptu.exe.skillswap.modules.booking.repository.MentorAvailabilityRuleRepository;
import com.fptu.exe.skillswap.modules.booking.repository.MentorAvailabilitySlotRepository;
import com.fptu.exe.skillswap.modules.booking.service.MentorAvailabilityService;
import com.fptu.exe.skillswap.modules.booking.support.AvailabilityCalendarWindowCalculator;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.domain.UserStatus;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorProfile;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorService;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorStatus;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorProfileRepository;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorServiceRepository;
import com.fptu.exe.skillswap.modules.notification.domain.NotificationType;
import com.fptu.exe.skillswap.modules.notification.service.NotificationService;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MentorAvailabilityServiceTest {

    @Mock
    private MentorProfileRepository mentorProfileRepository;

    @Mock
    private MentorAvailabilityRuleRepository mentorAvailabilityRuleRepository;

    @Mock
    private MentorAvailabilitySlotRepository mentorAvailabilitySlotRepository;

    @Mock
    private AvailabilitySlotServiceRepository availabilitySlotServiceRepository;

    @Mock
    private MentorServiceRepository mentorServiceRepository;

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private NotificationService notificationService;

    private final AvailabilityCalendarWindowCalculator calendarWindowCalculator = new AvailabilityCalendarWindowCalculator();

    private MentorAvailabilityService mentorAvailabilityService;

    private UUID mentorUserId;
    private MentorProfile mentorProfile;
    private User user;

    @BeforeEach
    void setUp() {
        mentorAvailabilityService = new MentorAvailabilityService(
                mentorProfileRepository,
                mentorAvailabilityRuleRepository,
                mentorAvailabilitySlotRepository,
                availabilitySlotServiceRepository,
                mentorServiceRepository,
                bookingRepository,
                notificationService,
                calendarWindowCalculator
        );

        mentorUserId = UUID.randomUUID();
        user = new User();
        user.setId(mentorUserId);
        user.setStatus(UserStatus.ACTIVE);

        mentorProfile = new MentorProfile();
        mentorProfile.setUserId(mentorUserId);
        mentorProfile.setUser(user);
        mentorProfile.setStatus(MentorStatus.ACTIVE);
        mentorProfile.setVerifiedAt(LocalDateTime.now());
    }

    @Test
    void createSlotDirectly_success() {
        // Arrange
        CreateAvailabilitySlotRequest request = new CreateAvailabilitySlotRequest(
                LocalDateTime.now().plusDays(1),
                LocalDateTime.now().plusDays(1).plusHours(2),
                "Note",
                List.of()
        );

        when(mentorProfileRepository.findWithUserByUserId(mentorUserId))
                .thenReturn(Optional.of(mentorProfile));
        when(mentorAvailabilitySlotRepository.existsOverlappingActiveSlot(mentorUserId, request.startTime(), request.endTime()))
                .thenReturn(false);
        when(mentorServiceRepository.findByMentorProfileUserIdAndIsActiveTrueOrderByCreatedAtAsc(mentorUserId))
                .thenReturn(List.of());

        when(mentorAvailabilityRuleRepository.save(any(MentorAvailabilityRule.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        MentorAvailabilitySlot expectedSlot = MentorAvailabilitySlot.builder()
                .id(UUID.randomUUID())
                .mentorProfile(mentorProfile)
                .startTime(request.startTime())
                .endTime(request.endTime())
                .isActive(true)
                .isBooked(false)
                .note(request.note())
                .build();
        when(mentorAvailabilitySlotRepository.save(any(MentorAvailabilitySlot.class)))
                .thenReturn(expectedSlot);

        // Act
        MentorManagedAvailabilitySlotResponse response = mentorAvailabilityService.createSlotDirectly(mentorUserId, request);

        // Assert
        assertNotNull(response);
        assertEquals(expectedSlot.getId(), response.slotId());
        assertEquals(expectedSlot.getStartTime(), response.startTime());
        assertEquals(expectedSlot.getEndTime(), response.endTime());
        assertEquals("Note", response.note());
    }

    @Test
    void createSlotDirectly_overlap_throwsConflict() {
        // Arrange
        CreateAvailabilitySlotRequest request = new CreateAvailabilitySlotRequest(
                LocalDateTime.now().plusDays(1),
                LocalDateTime.now().plusDays(1).plusHours(2),
                "Note",
                List.of()
        );

        when(mentorProfileRepository.findWithUserByUserId(mentorUserId))
                .thenReturn(Optional.of(mentorProfile));
        when(mentorAvailabilitySlotRepository.existsOverlappingActiveSlot(mentorUserId, request.startTime(), request.endTime()))
                .thenReturn(true);

        // Act & Assert
        BaseException ex = assertThrows(BaseException.class, () ->
                mentorAvailabilityService.createSlotDirectly(mentorUserId, request));
        assertEquals(ErrorCode.RESOURCE_CONFLICT, ex.getErrorCode());
    }

    @Test
    void createSlotDirectly_withServices_assignsCompositeIdsIntoManagedSlotCollection() {
        CreateAvailabilitySlotRequest request = new CreateAvailabilitySlotRequest(
                LocalDateTime.now().plusDays(1),
                LocalDateTime.now().plusDays(1).plusHours(2),
                "Note",
                List.of(UUID.randomUUID())
        );

        MentorService mentorService = new MentorService();
        UUID serviceId = UUID.randomUUID();
        mentorService.setId(serviceId);
        mentorService.setMentorProfile(mentorProfile);
        mentorService.setActive(true);

        when(mentorProfileRepository.findWithUserByUserId(mentorUserId))
                .thenReturn(Optional.of(mentorProfile));
        when(mentorAvailabilitySlotRepository.existsOverlappingActiveSlot(mentorUserId, request.startTime(), request.endTime()))
                .thenReturn(false);
        when(mentorServiceRepository.findAllById(request.serviceIds()))
                .thenReturn(List.of(mentorService));
        when(mentorAvailabilityRuleRepository.save(any(MentorAvailabilityRule.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        UUID slotId = UUID.randomUUID();
        MentorAvailabilitySlot savedSlot = MentorAvailabilitySlot.builder()
                .id(slotId)
                .mentorProfile(mentorProfile)
                .startTime(request.startTime())
                .endTime(request.endTime())
                .isActive(true)
                .isBooked(false)
                .note(request.note())
                .build();
        when(mentorAvailabilitySlotRepository.save(any(MentorAvailabilitySlot.class)))
                .thenReturn(savedSlot);

        mentorAvailabilityService.createSlotDirectly(mentorUserId, request);

        assertEquals(1, savedSlot.getSlotServices().size());
        AvailabilitySlotService binding = savedSlot.getSlotServices().iterator().next();
        assertNotNull(binding.getId());
        assertEquals(slotId, binding.getId().getSlotId());
        assertEquals(serviceId, binding.getId().getServiceId());
        verify(availabilitySlotServiceRepository, never()).saveAll(any());
    }

    @Test
    void updateSlotDirectly_success() {
        // Arrange
        UUID slotId = UUID.randomUUID();
        UpdateAvailabilitySlotRequest request = new UpdateAvailabilitySlotRequest(
                LocalDateTime.now().plusDays(2),
                LocalDateTime.now().plusDays(2).plusHours(2),
                "Updated Note",
                List.of()
        );

        MentorAvailabilityRule rule = MentorAvailabilityRule.builder().id(UUID.randomUUID()).build();
        MentorAvailabilitySlot slot = MentorAvailabilitySlot.builder()
                .id(slotId)
                .mentorProfile(mentorProfile)
                .rule(rule)
                .startTime(LocalDateTime.now().plusDays(1))
                .endTime(LocalDateTime.now().plusDays(1).plusHours(2))
                .isActive(true)
                .isBooked(false)
                .build();

        when(mentorAvailabilitySlotRepository.findById(slotId)).thenReturn(Optional.of(slot));
        when(mentorAvailabilitySlotRepository.existsOverlappingActiveSlotExcludeSelf(mentorUserId, slotId, request.startTime(), request.endTime()))
                .thenReturn(false);
        when(mentorServiceRepository.findByMentorProfileUserIdAndIsActiveTrueOrderByCreatedAtAsc(mentorUserId))
                .thenReturn(List.of());

        when(mentorAvailabilitySlotRepository.save(any(MentorAvailabilitySlot.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        MentorManagedAvailabilitySlotResponse response = mentorAvailabilityService.updateSlotDirectly(mentorUserId, slotId, request);

        // Assert
        assertNotNull(response);
        assertEquals(slotId, response.slotId());
        assertEquals(request.startTime(), response.startTime());
        assertEquals(request.endTime(), response.endTime());
        assertEquals("Updated Note", response.note());
    }

    @Test
    void updateSlotDirectly_bookedSlot_throwsBadRequest() {
        // Arrange
        UUID slotId = UUID.randomUUID();
        UpdateAvailabilitySlotRequest request = new UpdateAvailabilitySlotRequest(
                LocalDateTime.now().plusDays(2),
                LocalDateTime.now().plusDays(2).plusHours(2),
                "Updated Note",
                List.of()
        );

        MentorAvailabilitySlot slot = MentorAvailabilitySlot.builder()
                .id(slotId)
                .mentorProfile(mentorProfile)
                .isActive(true)
                .isBooked(true)
                .build();

        when(mentorAvailabilitySlotRepository.findById(slotId)).thenReturn(Optional.of(slot));

        // Act & Assert
        BaseException ex = assertThrows(BaseException.class, () ->
                mentorAvailabilityService.updateSlotDirectly(mentorUserId, slotId, request));
        assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
    }

    @Test
    void deleteSlotDirectly_success() {
        // Arrange
        UUID slotId = UUID.randomUUID();
        MentorAvailabilityRule rule = MentorAvailabilityRule.builder().id(UUID.randomUUID()).build();
        MentorAvailabilitySlot slot = MentorAvailabilitySlot.builder()
                .id(slotId)
                .mentorProfile(mentorProfile)
                .rule(rule)
                .isActive(true)
                .isBooked(false)
                .build();

        User mentee = new User();
        mentee.setId(UUID.randomUUID());
        Booking pendingBooking = Booking.builder()
                .id(UUID.randomUUID())
                .mentee(mentee)
                .status(BookingStatus.PENDING)
                .build();

        when(mentorAvailabilitySlotRepository.findById(slotId)).thenReturn(Optional.of(slot));
        when(bookingRepository.findBySlotIdAndStatus(slotId, BookingStatus.PENDING))
                .thenReturn(List.of(pendingBooking));

        // Act
        mentorAvailabilityService.deleteSlotDirectly(mentorUserId, slotId);

        // Assert
        verify(bookingRepository).save(pendingBooking);
        assertEquals(BookingStatus.REJECTED, pendingBooking.getStatus());
        verify(notificationService).createNotification(
                eq(mentee.getId()),
                eq(NotificationType.BOOKING_REJECTED),
                any(), any(), any(), any()
        );
        verify(mentorAvailabilitySlotRepository).save(slot);
        assertEquals(false, slot.isActive());
        verify(mentorAvailabilityRuleRepository).save(rule);
        assertEquals(false, rule.isActive());
    }

    @Test
    void deleteSlotDirectly_bookedSlot_throwsBadRequest() {
        // Arrange
        UUID slotId = UUID.randomUUID();
        MentorAvailabilitySlot slot = MentorAvailabilitySlot.builder()
                .id(slotId)
                .mentorProfile(mentorProfile)
                .isActive(true)
                .isBooked(true)
                .build();

        when(mentorAvailabilitySlotRepository.findById(slotId)).thenReturn(Optional.of(slot));

        // Act & Assert
        BaseException ex = assertThrows(BaseException.class, () ->
                mentorAvailabilityService.deleteSlotDirectly(mentorUserId, slotId));
        assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
    }

    @Test
    void getMySlots_success() {
        // Arrange
        LocalDate fromDate = LocalDate.now();
        LocalDate toDate = LocalDate.now().plusDays(7);

        when(mentorAvailabilitySlotRepository.findMyManagedSlotsWithServices(eq(mentorUserId), any(), any()))
                .thenReturn(new ArrayList<>());

        // Act
        List<MentorManagedAvailabilitySlotResponse> responses = mentorAvailabilityService.getMySlots(mentorUserId, fromDate, toDate);

        // Assert
        assertNotNull(responses);
        assertTrue(responses.isEmpty());
    }
}
