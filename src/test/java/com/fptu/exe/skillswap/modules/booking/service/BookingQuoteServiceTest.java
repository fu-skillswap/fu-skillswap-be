package com.fptu.exe.skillswap.modules.booking.service;

import com.fptu.exe.skillswap.modules.booking.domain.MentorAvailabilitySlot;
import com.fptu.exe.skillswap.modules.booking.dto.request.BookingQuoteRequest;
import com.fptu.exe.skillswap.modules.booking.dto.response.BookingQuoteResponse;
import com.fptu.exe.skillswap.modules.booking.port.BookingPricingEstimate;
import com.fptu.exe.skillswap.modules.booking.port.BookingPricingPreviewPort;
import com.fptu.exe.skillswap.modules.booking.repository.BookingRepository;
import com.fptu.exe.skillswap.modules.booking.repository.MentorAvailabilitySlotRepository;
import com.fptu.exe.skillswap.modules.identity.port.UserQueryPort;
import com.fptu.exe.skillswap.modules.identity.port.UserSummaryRecord;
import com.fptu.exe.skillswap.modules.mentor.port.MentorBookingCapability;
import com.fptu.exe.skillswap.modules.mentor.port.MentorBookingPolicyQuery;
import com.fptu.exe.skillswap.modules.mentor.port.MentorBookingQueryPort;
import com.fptu.exe.skillswap.modules.mentor.port.ServiceSlotCandidate;
import com.fptu.exe.skillswap.shared.constant.RoleCode;
import com.fptu.exe.skillswap.shared.time.TimeProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingQuoteServiceTest {

    @Mock
    private UserQueryPort userQueryPort;

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private MentorAvailabilitySlotRepository mentorAvailabilitySlotRepository;

    @Mock
    private MentorBookingQueryPort mentorBookingQueryPort;

    @Mock
    private BookingEligibilityPolicy bookingEligibilityPolicy;

    @Mock
    private BookingSlotValidator bookingSlotValidator;

    @Mock
    private MentorBookingPolicyQuery mentorBookingPolicyQuery;

    @Mock
    private BookingPricingPreviewPort pricingPreviewPort;

    @InjectMocks
    private BookingQuoteService bookingQuoteService;

    private final Instant now = Instant.parse("2026-09-01T10:00:00Z");

    @BeforeEach
    void setUp() {
        bookingQuoteService.setTimeProvider(TimeProvider.from(Clock.fixed(now, ZoneOffset.UTC)));
    }

    @Test
    void quote_shouldQueryPricingViaPortAndReturnCorrectQuoteResponse() {
        UUID menteeUserId = UUID.randomUUID();
        UUID mentorUserId = UUID.randomUUID();
        UUID slotId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        Instant startAt = now.plusSeconds(86400); // 24 hours later

        UserSummaryRecord mentee = new UserSummaryRecord(
                menteeUserId, "mentee@test.com", "Mentee Name", null, Set.of(RoleCode.MENTEE), "ACTIVE", true);
        UserSummaryRecord mentor = new UserSummaryRecord(
                mentorUserId, "mentor@test.com", "Mentor Name", null, Set.of(RoleCode.MENTOR), "ACTIVE", true);

        MentorAvailabilitySlot slot = new MentorAvailabilitySlot();
        slot.setId(slotId);
        slot.setMentorUserId(mentorUserId);
        slot.setActive(true);
        slot.setStartTimeUtc(startAt.minusSeconds(3600));
        slot.setEndTimeUtc(startAt.plusSeconds(7200));

        ServiceSlotCandidate serviceCandidate = new ServiceSlotCandidate(
                serviceId, mentorUserId, "Java Mentoring", "Description", "Outcome",
                60, 50000, false, true, "ONLINE", "ONE_ON_ONE", false);

        MentorBookingCapability capability = new MentorBookingCapability(
                mentorUserId, "APPROVED", true, 60, BigDecimal.valueOf(5.0), 10, null, true, true);

        BookingPricingEstimate estimate = new BookingPricingEstimate(
                "v1",
                OffsetDateTime.now(ZoneOffset.UTC),
                serviceId,
                55000,
                55000,
                5000,
                50000,
                "Welcome Bonus",
                true,
                BookingPricingPreviewPort.ESTIMATE_DISCLAIMER
        );

        when(userQueryPort.findUserSummaryById(menteeUserId)).thenReturn(Optional.of(mentee));
        when(userQueryPort.findUserSummaryById(mentorUserId)).thenReturn(Optional.of(mentor));
        when(mentorAvailabilitySlotRepository.findById(slotId)).thenReturn(Optional.of(slot));
        when(mentorBookingQueryPort.getActiveServiceCandidate(serviceId, mentorUserId)).thenReturn(Optional.of(serviceCandidate));
        when(mentorBookingQueryPort.getBookingCapability(mentorUserId)).thenReturn(Optional.of(capability));
        when(bookingEligibilityPolicy.isDiscoverableMentorForBooking(capability)).thenReturn(true);
        when(pricingPreviewPort.estimateForCandidate(eq(menteeUserId), any(ServiceSlotCandidate.class))).thenReturn(estimate);

        BookingQuoteRequest request = new BookingQuoteRequest(slotId, serviceId, startAt);
        BookingQuoteResponse response = bookingQuoteService.quote(menteeUserId, request);

        assertNotNull(response);
        assertEquals(slotId, response.slotId());
        assertEquals(serviceId, response.serviceId());
        assertEquals("Java Mentoring", response.serviceTitle());
        assertEquals(60, response.durationMinutes());
        assertTrue(response.isEstimate());
        assertEquals(BookingPricingPreviewPort.ESTIMATE_DISCLAIMER, response.disclaimer());
        assertNotNull(response.pricing());
        assertEquals(55000, response.pricing().priceScoin());
        assertEquals(50000, response.pricing().estimatedPayableScoin());
        assertEquals("Welcome Bonus", response.pricing().campaignName());

        verify(pricingPreviewPort).estimateForCandidate(eq(menteeUserId), eq(serviceCandidate));
    }
}
