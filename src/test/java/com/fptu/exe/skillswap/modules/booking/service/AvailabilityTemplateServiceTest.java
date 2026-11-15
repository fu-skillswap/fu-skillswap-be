package com.fptu.exe.skillswap.modules.booking.service;

import com.fptu.exe.skillswap.infrastructure.config.AvailabilityTemplateProperties;
import com.fptu.exe.skillswap.modules.booking.domain.AvailabilityMentorMutationLock;
import com.fptu.exe.skillswap.modules.booking.domain.AvailabilityTemplate;
import com.fptu.exe.skillswap.modules.booking.domain.MentorAvailabilitySlot;
import com.fptu.exe.skillswap.modules.booking.dto.request.CreateAvailabilityTemplateRequest;
import com.fptu.exe.skillswap.modules.booking.dto.response.AvailabilityTemplateReplacementConflictResponse;
import com.fptu.exe.skillswap.modules.booking.repository.AvailabilityMentorMutationLockRepository;
import com.fptu.exe.skillswap.modules.booking.repository.AvailabilitySlotServiceRepository;
import com.fptu.exe.skillswap.modules.booking.repository.AvailabilityTemplateExceptionRepository;
import com.fptu.exe.skillswap.modules.booking.repository.AvailabilityTemplateReconciliationRepository;
import com.fptu.exe.skillswap.modules.booking.repository.AvailabilityTemplateRepository;
import com.fptu.exe.skillswap.modules.booking.repository.BookingRepository;
import com.fptu.exe.skillswap.modules.booking.repository.MentorAvailabilitySlotRepository;
import com.fptu.exe.skillswap.modules.mentor.port.MentorBookingPolicyQuery;
import com.fptu.exe.skillswap.modules.mentor.port.MentorBookingQueryPort;
import com.fptu.exe.skillswap.modules.mentor.port.ServiceSlotCandidate;
import com.fptu.exe.skillswap.shared.cursor.CursorCodec;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import com.fptu.exe.skillswap.shared.exception.GeneratedOccurrenceReplacementException;
import com.fptu.exe.skillswap.shared.time.TimeProvider;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AvailabilityTemplateServiceTest {

    @Mock
    private AvailabilityTemplateRepository templateRepository;
    @Mock
    private AvailabilityTemplateExceptionRepository exceptionRepository;
    @Mock
    private AvailabilityTemplateReconciliationRepository reconciliationRepository;
    @Mock
    private AvailabilityMentorMutationLockRepository mutationLockRepository;
    @Mock
    private MentorAvailabilitySlotRepository slotRepository;
    @Mock
    private AvailabilitySlotServiceRepository slotServiceRepository;
    @Mock
    private MentorBookingQueryPort mentorBookingQueryPort;
    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private MentorBookingPolicyQuery mentorBookingPolicyQuery;
    @Mock
    private EntityManager entityManager;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private CursorCodec cursorCodec;

    private AvailabilityTemplateService service;
    private UUID mentorUserId;

    @BeforeEach
    void setUp() {
        mentorUserId = UUID.randomUUID();
        service = new AvailabilityTemplateService(
                templateRepository,
                exceptionRepository,
                reconciliationRepository,
                mutationLockRepository,
                slotRepository,
                slotServiceRepository,
                mentorBookingQueryPort,
                bookingRepository,
                mentorBookingPolicyQuery,
                new AvailabilityTemplateProperties("TEMPLATES", 28, 50, 1000, 120),
                entityManager,
                eventPublisher,
                cursorCodec,
                new SimpleMeterRegistry()
        );
        service.setTimeProvider(TimeProvider.from(Clock.fixed(Instant.parse("2026-11-15T01:00:00Z"), ZoneOffset.UTC)));
    }

    @Test
    void create_unknownMentor_throwsNotFoundBeforeAcquiringMutationLock() {
        UUID serviceId = UUID.randomUUID();
        CreateAvailabilityTemplateRequest request = new CreateAvailabilityTemplateRequest(
                LocalTime.of(9, 0),
                LocalTime.of(11, 0),
                List.of(DayOfWeek.MONDAY),
                LocalDate.of(2026, 11, 16),
                null,
                "Morning",
                List.of(serviceId)
        );
        when(mentorBookingQueryPort.existsById(mentorUserId)).thenReturn(false);

        BaseException ex = assertThrows(BaseException.class, () -> service.create(mentorUserId, request));

        assertEquals(ErrorCode.NOT_FOUND, ex.getErrorCode());
        verifyNoInteractions(mutationLockRepository);
    }

    @Test
    void create_windowShorterThanServiceDuration_throwsInvalidSchedule() {
        UUID serviceId = UUID.randomUUID();
        CreateAvailabilityTemplateRequest request = new CreateAvailabilityTemplateRequest(
                LocalTime.of(9, 0),
                LocalTime.of(9, 45),
                List.of(DayOfWeek.MONDAY),
                LocalDate.of(2026, 11, 16),
                null,
                "Too short",
                List.of(serviceId)
        );
        when(mentorBookingQueryPort.existsById(mentorUserId)).thenReturn(true);
        when(mutationLockRepository.findByMentorUserIdForUpdate(mentorUserId))
                .thenReturn(java.util.Optional.of(new AvailabilityMentorMutationLock(mentorUserId)));
        when(templateRepository.findOwnedWithServices(mentorUserId)).thenReturn(List.of());
        when(mentorBookingQueryPort.getServicesByIds(request.serviceIds())).thenReturn(Map.of(
                serviceId,
                new ServiceSlotCandidate(
                        serviceId,
                        mentorUserId,
                        "Deep Dive",
                        null,
                        null,
                        60,
                        100,
                        false,
                        true,
                        "ONE_TO_ONE",
                        null,
                        false
                )
        ));

        BaseException ex = assertThrows(BaseException.class, () -> service.create(mentorUserId, request));

        assertEquals(ErrorCode.AVAILABILITY_TEMPLATE_INVALID_SCHEDULE, ex.getErrorCode());
        verify(templateRepository, never()).saveAndFlush(any());
    }

    @Test
    void replaceGeneratedOccurrences_whenNotConfirmedAndNoManualOverlap_throwsReplacementRequired() {
        UUID templateId = UUID.randomUUID();
        LocalDate occurrenceDate = LocalDate.of(2026, 11, 20);
        LocalDateTime start = LocalDateTime.of(2026, 11, 20, 9, 0);
        LocalDateTime end = LocalDateTime.of(2026, 11, 20, 11, 0);
        AvailabilityTemplate template = AvailabilityTemplate.builder()
                .id(templateId)
                .mentorUserId(mentorUserId)
                .configVersion(3)
                .build();
        MentorAvailabilitySlot generatedSlot = MentorAvailabilitySlot.builder()
                .id(UUID.randomUUID())
                .mentorUserId(mentorUserId)
                .template(template)
                .templateOccurrenceDate(occurrenceDate)
                .startTime(start)
                .endTime(end)
                .isActive(true)
                .build();

        when(slotRepository.findActiveGeneratedOverlaps(mentorUserId, BookingTime.toInstant(start), BookingTime.toInstant(end)))
                .thenReturn(List.of(generatedSlot));
        when(slotRepository.findActiveManualOverlaps(mentorUserId, BookingTime.toInstant(start), BookingTime.toInstant(end)))
                .thenReturn(List.of());

        GeneratedOccurrenceReplacementException ex = assertThrows(
                GeneratedOccurrenceReplacementException.class,
                () -> service.replaceGeneratedOccurrences(mentorUserId, start, end, null, false, false, List.of())
        );

        assertEquals(ErrorCode.GENERATED_OCCURRENCE_REPLACEMENT_REQUIRED, ex.getErrorCode());
        @SuppressWarnings("unchecked")
        List<AvailabilityTemplateReplacementConflictResponse> conflicts =
                (List<AvailabilityTemplateReplacementConflictResponse>) ex.getData();
        assertEquals(1, conflicts.size());
        assertEquals(templateId, conflicts.getFirst().templateId());
        assertEquals(occurrenceDate, conflicts.getFirst().occurrenceDate());
        assertEquals(3, conflicts.getFirst().currentVersion());
    }

    @Test
    void replaceGeneratedOccurrences_whenNotConfirmedButManualOverlapExists_doesNotThrow() {
        LocalDateTime start = LocalDateTime.of(2026, 11, 20, 9, 0);
        LocalDateTime end = LocalDateTime.of(2026, 11, 20, 11, 0);
        AvailabilityTemplate template = AvailabilityTemplate.builder()
                .id(UUID.randomUUID())
                .mentorUserId(mentorUserId)
                .configVersion(1)
                .build();
        MentorAvailabilitySlot generatedSlot = MentorAvailabilitySlot.builder()
                .id(UUID.randomUUID())
                .mentorUserId(mentorUserId)
                .template(template)
                .templateOccurrenceDate(LocalDate.of(2026, 11, 20))
                .isActive(true)
                .build();
        MentorAvailabilitySlot otherManualSlot = MentorAvailabilitySlot.builder()
                .id(UUID.randomUUID())
                .mentorUserId(mentorUserId)
                .isActive(true)
                .build();

        when(slotRepository.findActiveGeneratedOverlaps(mentorUserId, BookingTime.toInstant(start), BookingTime.toInstant(end)))
                .thenReturn(List.of(generatedSlot));
        when(slotRepository.findActiveManualOverlaps(mentorUserId, BookingTime.toInstant(start), BookingTime.toInstant(end)))
                .thenReturn(List.of(otherManualSlot));

        assertDoesNotThrow(() ->
                service.replaceGeneratedOccurrences(mentorUserId, start, end, null, false, false, List.of()));
        verifyNoInteractions(exceptionRepository);
    }
}
