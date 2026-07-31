package com.fptu.exe.skillswap.modules.booking;

import com.fptu.exe.skillswap.modules.admin.service.AdminAuditWriterService;
import com.fptu.exe.skillswap.modules.booking.domain.GroupSession;
import com.fptu.exe.skillswap.modules.booking.domain.GroupSessionStatus;
import com.fptu.exe.skillswap.modules.booking.domain.MentorAvailabilitySlot;
import com.fptu.exe.skillswap.modules.booking.dto.request.CreateGroupSessionRequest;
import com.fptu.exe.skillswap.modules.booking.dto.request.GroupSessionVersionRequest;
import com.fptu.exe.skillswap.modules.booking.repository.AvailabilitySlotServiceRepository;
import com.fptu.exe.skillswap.modules.booking.repository.BookingRepository;
import com.fptu.exe.skillswap.modules.booking.repository.GroupSessionRepository;
import com.fptu.exe.skillswap.modules.booking.repository.MentorAvailabilitySlotRepository;
import com.fptu.exe.skillswap.modules.booking.service.GroupSessionManagementService;
import com.fptu.exe.skillswap.modules.booking.service.GroupSessionCommerceService;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorProfile;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorService;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorServiceDeliveryMode;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorStatus;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorProfileRepository;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorServiceRepository;
import com.fptu.exe.skillswap.modules.mentor.service.MentorBookingPolicyService;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class GroupSessionManagementServiceTest {

    @Mock private MentorProfileRepository mentorProfileRepository;
    @Mock private MentorServiceRepository mentorServiceRepository;
    @Mock private MentorAvailabilitySlotRepository slotRepository;
    @Mock private AvailabilitySlotServiceRepository slotServiceRepository;
    @Mock private BookingRepository bookingRepository;
    @Mock private GroupSessionRepository groupSessionRepository;
    @Mock private MentorBookingPolicyService mentorBookingPolicyService;
    @Mock private AdminAuditWriterService auditWriter;
    @Mock private GroupSessionCommerceService groupSessionCommerceService;

    private GroupSessionManagementService service;
    private UUID mentorId;
    private UUID serviceId;
    private UUID slotId;
    private MentorService mentorService;
    private MentorAvailabilitySlot slot;

    @BeforeEach
    void setUp() {
        service = new GroupSessionManagementService(mentorProfileRepository, mentorServiceRepository, slotRepository,
                slotServiceRepository, bookingRepository, groupSessionRepository, mentorBookingPolicyService, auditWriter,
                groupSessionCommerceService);
        mentorId = UUID.randomUUID();
        serviceId = UUID.randomUUID();
        slotId = UUID.randomUUID();
        MentorProfile mentor = MentorProfile.builder().userId(mentorId).status(MentorStatus.ACTIVE)
                .verifiedAt(LocalDateTime.now().minusDays(1)).build();
        mentorService = MentorService.builder().id(serviceId).mentorProfile(mentor).title("Java interview drills")
                .description("Practice interviews").expectedOutcome("Confident answers").durationMinutes(60)
                .isFree(false).priceScoin(120).isActive(true).deliveryMode(MentorServiceDeliveryMode.GROUP_SESSION).build();
        slot = MentorAvailabilitySlot.builder().id(slotId).mentorProfile(mentor)
                .startTime(LocalDateTime.of(2027, 1, 10, 10, 0))
                .endTime(LocalDateTime.of(2027, 1, 10, 14, 0)).isActive(true).build();
        when(mentorProfileRepository.findWithUserByUserIdForUpdate(mentorId)).thenReturn(Optional.of(mentor));
        when(mentorServiceRepository.findByIdAndMentorProfileUserIdForUpdate(serviceId, mentorId)).thenReturn(Optional.of(mentorService));
        when(slotRepository.findByIdForUpdate(slotId)).thenReturn(Optional.of(slot));
        when(slotServiceRepository.existsBySlotIdAndServiceId(slotId, serviceId)).thenReturn(true);
        lenient().when(groupSessionRepository.findActiveOverlapsForUpdate(eq(mentorId), any(), any(), any())).thenReturn(List.of());
        lenient().when(bookingRepository.findOverlappingBySlotIdAndStatusForUpdate(eq(slotId), any(), any(), any())).thenReturn(List.of());
        lenient().when(groupSessionRepository.save(any(GroupSession.class))).thenAnswer(invocation -> {
            GroupSession value = invocation.getArgument(0);
            if (value.getId() == null) value.setId(UUID.randomUUID());
            return value;
        });
    }

    @Test
    void createDraft_derivesEndAndDefaultRegistrationDeadline() {
        Instant start = Instant.parse("2027-01-10T12:00:00Z");
        service.create(mentorId, serviceId, new CreateGroupSessionRequest(slotId, start, 10, null, "Interview drills"));

        ArgumentCaptor<GroupSession> captor = ArgumentCaptor.forClass(GroupSession.class);
        org.mockito.Mockito.verify(groupSessionRepository).save(captor.capture());
        assertEquals(GroupSessionStatus.DRAFT, captor.getValue().getStatus());
        assertEquals(LocalDateTime.ofInstant(start, ZoneOffset.UTC).plusMinutes(60), captor.getValue().getScheduledEndAt());
        assertEquals(LocalDateTime.ofInstant(start, ZoneOffset.UTC).minusHours(1), captor.getValue().getRegistrationClosesAt());
        assertEquals("Java interview drills", captor.getValue().getServiceTitleSnapshot());
        assertEquals(120, captor.getValue().getServicePriceScoinSnapshot());
    }

    @Test
    void createDraft_rejectsNonMinuteBoundary() {
        Instant invalid = Instant.parse("2027-01-10T12:00:01Z");
        assertThrows(BaseException.class, () -> service.create(mentorId, serviceId,
                new CreateGroupSessionRequest(slotId, invalid, 10, null, null)));
    }

    @Test
    void createDraft_rejectsOverlappingBooking() {
        when(bookingRepository.findOverlappingBySlotIdAndStatusForUpdate(eq(slotId), any(), any(), any()))
                .thenReturn(List.of(new com.fptu.exe.skillswap.modules.booking.domain.Booking()));
        assertThrows(BaseException.class, () -> service.create(mentorId, serviceId,
                new CreateGroupSessionRequest(slotId, Instant.parse("2027-01-10T12:00:00Z"), 10, null, null)));
    }
}
