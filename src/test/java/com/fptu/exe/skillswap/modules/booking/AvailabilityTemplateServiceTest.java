package com.fptu.exe.skillswap.modules.booking;

import com.fptu.exe.skillswap.infrastructure.config.AvailabilityTemplateProperties;
import com.fptu.exe.skillswap.modules.booking.domain.AvailabilityMentorMutationLock;
import com.fptu.exe.skillswap.modules.booking.dto.request.CreateAvailabilityTemplateRequest;
import com.fptu.exe.skillswap.modules.booking.repository.*;
import com.fptu.exe.skillswap.modules.booking.service.AvailabilityTemplateService;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorProfile;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorProfileRepository;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorServiceRepository;
import com.fptu.exe.skillswap.modules.mentor.service.MentorBookingPolicyService;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import com.fptu.exe.skillswap.shared.cursor.CursorCodec;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AvailabilityTemplateServiceTest {
    @Mock private AvailabilityTemplateRepository templateRepository;
    @Mock private AvailabilityTemplateExceptionRepository exceptionRepository;
    @Mock private AvailabilityTemplateReconciliationRepository reconciliationRepository;
    @Mock private AvailabilityMentorMutationLockRepository mutationLockRepository;
    @Mock private MentorAvailabilitySlotRepository slotRepository;
    @Mock private AvailabilitySlotServiceRepository slotServiceRepository;
    @Mock private MentorProfileRepository mentorProfileRepository;
    @Mock private MentorServiceRepository mentorServiceRepository;
    @Mock private BookingRepository bookingRepository;
    @Mock private GroupSessionRepository groupSessionRepository;
    @Mock private MentorBookingPolicyService mentorBookingPolicyService;
    @Mock private EntityManager entityManager;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private CursorCodec cursorCodec;

    private AvailabilityTemplateService service;
    private UUID mentorId;

    @BeforeEach
    void setUp() {
        mentorId = UUID.randomUUID();
        when(mutationLockRepository.findByMentorUserIdForUpdate(mentorId))
                .thenReturn(Optional.of(new AvailabilityMentorMutationLock(mentorId)));
        MentorProfile mentor = new MentorProfile();
        mentor.setUserId(mentorId);
        when(mentorProfileRepository.findWithUserByUserId(mentorId)).thenReturn(Optional.of(mentor));
        service = new AvailabilityTemplateService(templateRepository, exceptionRepository, reconciliationRepository,
                mutationLockRepository, slotRepository, slotServiceRepository, mentorProfileRepository,
                mentorServiceRepository, bookingRepository, groupSessionRepository, mentorBookingPolicyService,
                new AvailabilityTemplateProperties("TEMPLATES", 14, 50, 1000, 120), entityManager,
                eventPublisher, cursorCodec, new SimpleMeterRegistry());
    }

    @Test
    void createRejectsPastEffectiveFromWithoutPersistingTemplate() {
        CreateAvailabilityTemplateRequest request = new CreateAvailabilityTemplateRequest(
                LocalTime.of(9, 0), LocalTime.of(10, 0), List.of(DayOfWeek.MONDAY),
                LocalDate.now().minusDays(1), null, null, List.of(UUID.randomUUID()));

        BaseException exception = assertThrows(BaseException.class, () -> service.create(mentorId, request));

        assertEquals(ErrorCode.AVAILABILITY_TEMPLATE_INVALID_SCHEDULE, exception.getErrorCode());
        verify(templateRepository, never()).saveAndFlush(any());
    }
}
