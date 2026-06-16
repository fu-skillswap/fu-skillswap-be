package com.fptu.exe.skillswap.modules.booking;

import com.fptu.exe.skillswap.modules.booking.domain.AvailabilityRepeatType;
import com.fptu.exe.skillswap.modules.booking.domain.AvailabilityRuleType;
import com.fptu.exe.skillswap.modules.booking.domain.BookingStatus;
import com.fptu.exe.skillswap.modules.booking.dto.request.AcceptBookingRequest;
import com.fptu.exe.skillswap.modules.booking.dto.request.CreateBookingRequest;
import com.fptu.exe.skillswap.modules.booking.dto.request.SaveMeetingLinkRequest;
import com.fptu.exe.skillswap.modules.booking.dto.request.CompleteBookingRequest;
import com.fptu.exe.skillswap.modules.booking.dto.request.UpsertAvailabilityRuleRequest;
import com.fptu.exe.skillswap.modules.booking.dto.response.BookingResponse;
import com.fptu.exe.skillswap.modules.booking.domain.MeetingPlatform;
import com.fptu.exe.skillswap.modules.booking.domain.MentorAvailabilitySlot;
import com.fptu.exe.skillswap.modules.booking.repository.MentorAvailabilitySlotRepository;
import com.fptu.exe.skillswap.modules.booking.service.BookingService;
import com.fptu.exe.skillswap.modules.booking.service.MentorAvailabilityService;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.domain.UserStatus;
import com.fptu.exe.skillswap.modules.identity.repository.UserRepository;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorProfile;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorStatus;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class BookingFlowIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MentorProfileRepository mentorProfileRepository;

    @Autowired
    private MentorAvailabilityService mentorAvailabilityService;

    @Autowired
    private MentorAvailabilitySlotRepository mentorAvailabilitySlotRepository;

    @Autowired
    private BookingService bookingService;

    private User menteeUser;
    private User mentorUser;
    private MentorProfile mentorProfile;

    @BeforeEach
    void setUp() {
        // Create Mentee
        menteeUser = userRepository.save(User.builder()
                .email("mentee-booking@test.com")
                .fullName("Mentee Booker")
                .status(UserStatus.ACTIVE)
                .build());

        // Create Mentor
        mentorUser = userRepository.save(User.builder()
                .email("mentor-booking@test.com")
                .fullName("Active Mentor")
                .status(UserStatus.ACTIVE)
                .build());

        mentorProfile = mentorProfileRepository.save(MentorProfile.builder()
                .user(mentorUser)
                .status(MentorStatus.ACTIVE)
                .verifiedAt(LocalDateTime.now())
                .isAvailable(true)
                .sessionDuration(60)
                .build());
    }

    @Test
    void testCompleteBookingFlow() {
        UUID mentorId = mentorUser.getId();
        UUID menteeId = menteeUser.getId();

        // 1. Mentor configures availability rule
        LocalDate effectiveDate = LocalDate.now().plusDays(1);
        var ruleRequest = new UpsertAvailabilityRuleRequest(
                AvailabilityRuleType.OPEN,
                AvailabilityRepeatType.DAILY,
                null,
                effectiveDate,
                effectiveDate.plusDays(2),
                LocalTime.of(14, 0),
                LocalTime.of(16, 0),
                "Daily afternoon slots"
        );

        mentorAvailabilityService.createRule(mentorId, ruleRequest);

        // 2. Generate slots manually for that rule
        mentorAvailabilityService.generateSlotsForDateRange(mentorProfile, effectiveDate, effectiveDate.plusDays(2));

        // Retrieve the generated slots
        List<MentorAvailabilitySlot> slots = mentorAvailabilitySlotRepository
                .findByMentorProfileUserIdAndIsActiveTrueAndIsBookedFalseAndStartTimeAfterOrderByStartTimeAsc(
                        mentorId, LocalDateTime.now()
                );
        assertFalse(slots.isEmpty(), "Availability slots should have been generated");
        MentorAvailabilitySlot slotToBook = slots.get(0);

        // 3. Mentee creates booking
        CreateBookingRequest createRequest = new CreateBookingRequest(
                mentorId,
                slotToBook.getId(),
                null,
                "Need help with Java generics",
                "Wildcard boundaries explain"
        );

        BookingResponse booking = bookingService.createBooking(menteeId, createRequest);
        assertNotNull(booking);
        assertEquals(BookingStatus.PENDING, booking.status());
        assertTrue(mentorAvailabilitySlotRepository.findById(slotToBook.getId()).orElseThrow().isBooked());

        // 4. Mentor accepts booking
        BookingResponse accepted = bookingService.acceptBooking(
                mentorId, booking.bookingId(), new AcceptBookingRequest("Happy to help!")
        );
        assertEquals(BookingStatus.ACCEPTED, accepted.status());

        // 5. Mentor saves meeting link
        BookingResponse linked = bookingService.saveMeetingLink(
                mentorId, booking.bookingId(), new SaveMeetingLinkRequest(
                        MeetingPlatform.GOOGLE_MEET, "https://meet.google.com/abc-defg-hij", null
                )
        );
        assertEquals("https://meet.google.com/abc-defg-hij", linked.meetingLink());

        // 6. Mentor completes booking after meeting time
        // Modify booking requested time to past so we can complete it
        com.fptu.exe.skillswap.modules.booking.domain.Booking dbBooking =
                ((com.fptu.exe.skillswap.modules.booking.repository.BookingRepository)
                        org.springframework.test.util.ReflectionTestUtils.getField(bookingService, "bookingRepository"))
                        .findById(booking.bookingId()).orElseThrow();
        dbBooking.setRequestedStartTime(LocalDateTime.now().minusHours(2));

        BookingResponse completed = bookingService.completeBooking(
                mentorId, booking.bookingId(), new CompleteBookingRequest("Good session, code works")
        );
        assertEquals(BookingStatus.COMPLETED, completed.status());
        assertNotNull(completed.completedAt());
    }
}
