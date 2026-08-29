package com.fptu.exe.skillswap.modules.booking.service;

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
import com.fptu.exe.skillswap.modules.mentor.domain.MentorServiceDeliveryMode;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorStatus;
import com.fptu.exe.skillswap.modules.mentor.dto.response.ServiceSlotCandidatesResponse;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorProfileRepository;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorServiceRepository;
import com.fptu.exe.skillswap.modules.mentor.service.MentorBookingPolicyService;
import com.fptu.exe.skillswap.modules.notification.NotificationType;
import com.fptu.exe.skillswap.modules.notification.service.NotificationService;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import com.fptu.exe.skillswap.infrastructure.config.PaymentProperties;
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

    @Mock
    private MentorBookingPolicyService mentorBookingPolicyService;

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
                calendarWindowCalculator,
                new PaymentProperties(),
                mentorBookingPolicyService
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

        org.mockito.Mockito.lenient().when(mentorBookingPolicyService.getEffectivePolicy(any()))
                .thenReturn(new MentorBookingPolicyService.MentorBookingPolicySnapshot(120, 30, "Asia/Ho_Chi_Minh"));
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
        when(mentorAvailabilitySlotRepository.existsOverlappingActiveSlot(mentorUserId, BookingTime.toInstant(request.startTime()), BookingTime.toInstant(request.endTime())))
                .thenReturn(false);

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
        when(mentorAvailabilitySlotRepository.existsOverlappingActiveSlot(mentorUserId, BookingTime.toInstant(request.startTime()), BookingTime.toInstant(request.endTime())))
                .thenReturn(true);

        // Act & Assert
        BaseException ex = assertThrows(BaseException.class, () ->
                mentorAvailabilityService.createSlotDirectly(mentorUserId, request));
        assertEquals(ErrorCode.RESOURCE_CONFLICT, ex.getErrorCode());
    }

    @Test
    void createSlotDirectly_withServices_assignsCompositeIdsIntoManagedSlotCollection() {
        UUID serviceId = UUID.randomUUID();
        CreateAvailabilitySlotRequest request = new CreateAvailabilitySlotRequest(
                LocalDateTime.now().plusDays(1),
                LocalDateTime.now().plusDays(1).plusHours(2),
                "Note",
                List.of(serviceId)
        );

        MentorService mentorService = new MentorService();
        mentorService.setId(serviceId);
        mentorService.setMentorProfile(mentorProfile);
        mentorService.setActive(true);

        when(mentorProfileRepository.findWithUserByUserId(mentorUserId))
                .thenReturn(Optional.of(mentorProfile));
        when(mentorAvailabilitySlotRepository.existsOverlappingActiveSlot(mentorUserId, BookingTime.toInstant(request.startTime()), BookingTime.toInstant(request.endTime())))
                .thenReturn(false);
        when(mentorServiceRepository.findAllById(request.serviceIds()))
                .thenReturn(List.of(mentorService));
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
    void createSlotDirectly_withMissingServiceIds_shouldThrowBadRequest() {
        UUID requestedServiceId = UUID.randomUUID();
        CreateAvailabilitySlotRequest request = new CreateAvailabilitySlotRequest(
                LocalDateTime.now().plusDays(1),
                LocalDateTime.now().plusDays(1).plusHours(2),
                "Note",
                List.of(requestedServiceId)
        );

        when(mentorProfileRepository.findWithUserByUserId(mentorUserId))
                .thenReturn(Optional.of(mentorProfile));
        when(mentorAvailabilitySlotRepository.existsOverlappingActiveSlot(mentorUserId, BookingTime.toInstant(request.startTime()), BookingTime.toInstant(request.endTime())))
                .thenReturn(false);
        when(mentorServiceRepository.findAllById(request.serviceIds()))
                .thenReturn(List.of());

        BaseException ex = assertThrows(BaseException.class, () ->
                mentorAvailabilityService.createSlotDirectly(mentorUserId, request));

        assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
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
        when(mentorAvailabilitySlotRepository.existsOverlappingActiveSlotExcludeSelf(mentorUserId, slotId, BookingTime.toInstant(request.startTime()), BookingTime.toInstant(request.endTime())))
                .thenReturn(false);

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
    void updateSlotDirectly_withMissingServiceIds_shouldThrowBadRequest() {
        UUID slotId = UUID.randomUUID();
        UUID missingServiceId = UUID.randomUUID();
        UpdateAvailabilitySlotRequest request = new UpdateAvailabilitySlotRequest(
                LocalDateTime.now().plusDays(2),
                LocalDateTime.now().plusDays(2).plusHours(2),
                "Updated Note",
                List.of(missingServiceId)
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
        when(mentorAvailabilitySlotRepository.existsOverlappingActiveSlotExcludeSelf(mentorUserId, slotId, BookingTime.toInstant(request.startTime()), BookingTime.toInstant(request.endTime())))
                .thenReturn(false);
        when(mentorServiceRepository.findAllById(request.serviceIds()))
                .thenReturn(List.of());

        BaseException ex = assertThrows(BaseException.class, () ->
                mentorAvailabilityService.updateSlotDirectly(mentorUserId, slotId, request));

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
        List<MentorManagedAvailabilitySlotResponse> responses = mentorAvailabilityService.getMySlots(mentorUserId, null, fromDate, toDate);

        // Assert
        assertNotNull(responses);
        assertTrue(responses.isEmpty());
    }

    @Test
    void getServiceSlotCandidates_shouldUseOnlyAppAvailabilityRules() {
        // Arrange
        UUID slotId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();

        LocalDateTime now = LocalDateTime.now().withSecond(0).withNano(0);
        LocalDateTime slotStart = now.plusHours(1); // 1 hour ahead (60 min)
        LocalDateTime slotEnd = now.plusHours(4);   // 4 hours ahead (3 x 60min segments)

        MentorService service = MentorService.builder()
                .id(serviceId)
                .mentorProfile(mentorProfile)
                .title("1:1 Mentoring")
                .durationMinutes(60)
                .isActive(true)
                .deliveryMode(MentorServiceDeliveryMode.ONE_TO_ONE)
                .build();

        MentorAvailabilitySlot slot = MentorAvailabilitySlot.builder()
                .id(slotId)
                .mentorProfile(mentorProfile)
                .startTime(slotStart)
                .endTime(slotEnd)
                .isActive(true)
                .isBooked(false)
                .build();

        AvailabilitySlotService binding = AvailabilitySlotService.builder()
                .slot(slot)
                .service(service)
                .build();

        when(mentorAvailabilitySlotRepository.findById(slotId)).thenReturn(Optional.of(slot));
        when(availabilitySlotServiceRepository.findBySlotIdAndServiceId(slotId, serviceId)).thenReturn(Optional.of(binding));
        when(mentorBookingPolicyService.getEffectivePolicy(mentorUserId)).thenReturn(
                new MentorBookingPolicyService.MentorBookingPolicySnapshot(90, 30, "Asia/Ho_Chi_Minh") // 90 min lead time
        );

        when(bookingRepository.findBySlotIdAndStatusOrderBySelectedStartTimeAsc(eq(slotId), any())).thenReturn(List.of());
        when(bookingRepository.countPendingSegmentsBySlotId(eq(slotId), eq(BookingStatus.PENDING))).thenReturn(List.of());

        // Act
        ServiceSlotCandidatesResponse response = mentorAvailabilityService.getServiceSlotCandidates(mentorUserId, slotId, serviceId);

        // Assert
        assertNotNull(response);
        assertEquals(3, response.candidateServiceSlots().size());

        // Candidate 1 (now + 1h to now + 2h): blocked by lead time (90min required, but only 60min away)
        var candidate1 = response.candidateServiceSlots().get(0);
        org.junit.jupiter.api.Assertions.assertFalse(candidate1.isSelectable());
        assertEquals("Yêu cầu đặt trước tối thiểu", candidate1.reasonIfBlocked());
        assertTrue(candidate1.bookingConflictNote().contains("90 phút"));

        // Candidate 2 (now + 2h to now + 3h): valid and selectable (120min away > 90min required)
        var candidate2 = response.candidateServiceSlots().get(1);
        assertTrue(candidate2.isSelectable());
        org.junit.jupiter.api.Assertions.assertNull(candidate2.reasonIfBlocked());

        // Candidate 3 has no conflicting booking in SkillSwap, therefore stays selectable.
        var candidate3 = response.candidateServiceSlots().get(2);
        assertTrue(candidate3.isSelectable());
        org.junit.jupiter.api.Assertions.assertNull(candidate3.reasonIfBlocked());
    }
}
