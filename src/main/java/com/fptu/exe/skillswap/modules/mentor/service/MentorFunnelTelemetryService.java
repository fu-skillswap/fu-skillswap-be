package com.fptu.exe.skillswap.modules.mentor.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics;
import com.fptu.exe.skillswap.infrastructure.config.CacheProperties;
import com.fptu.exe.skillswap.modules.booking.port.BookingAvailabilityPort;
import com.fptu.exe.skillswap.modules.mentor.dto.request.MentorFunnelEventRequest;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorProfileRepository;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorServiceRepository;
import com.fptu.exe.skillswap.modules.system.port.TelemetryPort;
import com.fptu.exe.skillswap.shared.ratelimit.InMemoryRateLimitService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Best-effort analytics: invalid or overloaded telemetry is intentionally dropped, never user-facing. */
@Service
@Slf4j
public class MentorFunnelTelemetryService {
    private final MentorServiceRepository mentorServiceRepository;
    private final MentorProfileRepository mentorProfileRepository;
    private final BookingAvailabilityPort bookingAvailabilityPort;
    private final TelemetryPort internalTelemetryService;
    private final InMemoryRateLimitService rateLimitService;
    private final Cache<String, Boolean> dedupe;

    @Autowired
    public MentorFunnelTelemetryService(MentorServiceRepository mentorServiceRepository,
                                        MentorProfileRepository mentorProfileRepository,
                                        BookingAvailabilityPort bookingAvailabilityPort,
                                        TelemetryPort internalTelemetryService,
                                        InMemoryRateLimitService rateLimitService,
                                        CacheProperties cacheProperties,
                                        MeterRegistry meterRegistry) {
        this(mentorServiceRepository, mentorProfileRepository, bookingAvailabilityPort, internalTelemetryService,
                rateLimitService, cacheProperties, meterRegistry, true);
    }

    MentorFunnelTelemetryService(MentorServiceRepository mentorServiceRepository,
                                 MentorProfileRepository mentorProfileRepository,
                                 BookingAvailabilityPort bookingAvailabilityPort,
                                 TelemetryPort internalTelemetryService,
                                 InMemoryRateLimitService rateLimitService,
                                 CacheProperties cacheProperties) {
        this(mentorServiceRepository, mentorProfileRepository, bookingAvailabilityPort, internalTelemetryService,
                rateLimitService, cacheProperties, null, false);
    }

    private MentorFunnelTelemetryService(MentorServiceRepository mentorServiceRepository,
                                         MentorProfileRepository mentorProfileRepository,
                                         BookingAvailabilityPort bookingAvailabilityPort,
                                         TelemetryPort internalTelemetryService,
                                         InMemoryRateLimitService rateLimitService,
                                         CacheProperties cacheProperties,
                                         MeterRegistry meterRegistry,
                                         boolean monitorMetrics) {
        this.mentorServiceRepository = mentorServiceRepository;
        this.mentorProfileRepository = mentorProfileRepository;
        this.bookingAvailabilityPort = bookingAvailabilityPort;
        this.internalTelemetryService = internalTelemetryService;
        this.rateLimitService = rateLimitService;
        CacheProperties.TimedCache settings = cacheProperties.getMentorFunnelDedupe();
        this.dedupe = Caffeine.newBuilder()
                .maximumSize(settings.getMaximumSize())
                .expireAfterWrite(settings.getTtl())
                .recordStats()
                .build();
        if (monitorMetrics) {
            CaffeineCacheMetrics.monitor(meterRegistry, dedupe, "mentor-funnel-dedupe");
        }
    }

    public void recordClientEvent(UUID userId, MentorFunnelEventRequest request) {
        try {
            rateLimitService.check(com.fptu.exe.skillswap.shared.ratelimit.RateLimitScope.BEST_EFFORT, "mentor-funnel:" + userId, 30, Duration.ofMinutes(1), "Telemetry đang được gửi quá nhanh");
            UUID subjectId = request.slotId() != null ? request.slotId() : request.serviceId() != null ? request.serviceId() : request.mentorUserId();
            String key = userId + ":" + request.eventType() + ":" + subjectId;
            if (dedupe.asMap().putIfAbsent(key, Boolean.TRUE) != null) return;
            if (!isValidRelationship(request)) return;
            Map<String, String> metadata = new LinkedHashMap<>();
            metadata.put("source", request.source().name());
            if (request.serviceId() != null) metadata.put("serviceId", request.serviceId().toString());
            if (request.slotId() != null) metadata.put("slotId", request.slotId().toString());
            internalTelemetryService.record(request.eventType().name(), userId, "MENTOR", request.mentorUserId(), Map.copyOf(metadata));
        } catch (RuntimeException ex) {
            log.debug("Ignored mentor funnel telemetry failure: {}", ex.getMessage());
        }
    }

    private boolean isValidRelationship(MentorFunnelEventRequest request) {
        if (mentorProfileRepository.findWithUserByUserId(request.mentorUserId()).isEmpty()) return false;
        if (request.serviceId() != null && mentorServiceRepository.findByIdAndMentorProfileUserId(request.serviceId(), request.mentorUserId()).isEmpty()) return false;
        return true;
    }
}
