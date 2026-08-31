package com.fptu.exe.skillswap.modules.mentor.service;

import com.fptu.exe.skillswap.infrastructure.config.CacheProperties;
import com.fptu.exe.skillswap.modules.booking.port.BookingAvailabilityQueryPort;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorProfile;
import com.fptu.exe.skillswap.modules.mentor.dto.request.MentorFunnelEventRequest;
import com.fptu.exe.skillswap.modules.mentor.dto.request.MentorFunnelEventType;
import com.fptu.exe.skillswap.modules.mentor.dto.request.MentorFunnelSource;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorProfileRepository;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorServiceRepository;
import com.fptu.exe.skillswap.infrastructure.telemetry.InternalTelemetryService;
import com.fptu.exe.skillswap.shared.ratelimit.InMemoryRateLimitService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MentorFunnelTelemetryServiceTest {

    @Mock
    private MentorServiceRepository mentorServiceRepository;
    @Mock
    private MentorProfileRepository mentorProfileRepository;
    @Mock
    private BookingAvailabilityQueryPort bookingAvailabilityQueryPort;
    @Mock
    private InternalTelemetryService internalTelemetryService;
    @Mock
    private InMemoryRateLimitService rateLimitService;
    @Mock
    private MentorProfile mentorProfile;

    @Test
    void duplicateClientEvent_isDiscardedBeforeRelationshipQueries() {
        MentorFunnelTelemetryService service = new MentorFunnelTelemetryService(
                mentorServiceRepository,
                mentorProfileRepository,
                bookingAvailabilityQueryPort,
                internalTelemetryService,
                rateLimitService,
                new CacheProperties()
        );
        UUID userId = UUID.randomUUID();
        UUID mentorUserId = UUID.randomUUID();
        MentorFunnelEventRequest request = new MentorFunnelEventRequest(
                MentorFunnelEventType.SERVICE_VIEWED,
                mentorUserId,
                null,
                null,
                MentorFunnelSource.MENTOR_PROFILE
        );
        when(mentorProfileRepository.findWithUserByUserId(mentorUserId)).thenReturn(Optional.of(mentorProfile));

        service.recordClientEvent(userId, request);
        service.recordClientEvent(userId, request);

        verify(mentorProfileRepository, times(1)).findWithUserByUserId(mentorUserId);
        verify(internalTelemetryService, times(1)).record(
                any(), any(), any(), any(), any()
        );
    }
}
