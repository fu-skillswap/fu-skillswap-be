package com.fptu.exe.skillswap.modules.booking;

import com.fptu.exe.skillswap.modules.academic.dto.request.StudentProfileRequest;
import com.fptu.exe.skillswap.modules.academic.repository.AcademicProgramRepository;
import com.fptu.exe.skillswap.modules.academic.repository.CampusRepository;
import com.fptu.exe.skillswap.modules.academic.repository.SpecializationRepository;
import com.fptu.exe.skillswap.modules.academic.service.AcademicService;
import com.fptu.exe.skillswap.modules.booking.domain.AvailabilityRepeatType;
import com.fptu.exe.skillswap.modules.booking.domain.AvailabilityRuleType;
import com.fptu.exe.skillswap.modules.booking.domain.AvailabilitySlotService;
import com.fptu.exe.skillswap.modules.booking.domain.AvailabilitySlotServiceId;
import com.fptu.exe.skillswap.modules.booking.domain.BookingStatus;
import com.fptu.exe.skillswap.modules.booking.domain.MentorAvailabilityRule;
import com.fptu.exe.skillswap.modules.booking.dto.request.AcceptBookingRequest;
import com.fptu.exe.skillswap.modules.booking.dto.request.CreateBookingRequest;
import com.fptu.exe.skillswap.modules.booking.dto.request.SaveMeetingLinkRequest;
import com.fptu.exe.skillswap.modules.booking.dto.request.CompleteBookingRequest;
import com.fptu.exe.skillswap.modules.booking.dto.response.BookingResponse;
import com.fptu.exe.skillswap.modules.booking.domain.MeetingPlatform;
import com.fptu.exe.skillswap.modules.booking.domain.MentorAvailabilitySlot;
import com.fptu.exe.skillswap.modules.booking.repository.AvailabilitySlotServiceRepository;
import com.fptu.exe.skillswap.modules.booking.repository.MentorAvailabilityRuleRepository;
import com.fptu.exe.skillswap.modules.booking.repository.MentorAvailabilitySlotRepository;
import com.fptu.exe.skillswap.modules.booking.service.BookingService;
import com.fptu.exe.skillswap.modules.conversation.domain.ConversationSourceType;
import com.fptu.exe.skillswap.modules.conversation.repository.ConversationParticipantRepository;
import com.fptu.exe.skillswap.modules.conversation.repository.ConversationRepository;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.domain.UserStatus;
import com.fptu.exe.skillswap.modules.identity.repository.UserRepository;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorProfile;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorStatus;
import com.fptu.exe.skillswap.modules.mentor.domain.TeachingMode;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorProfileRepository;
import com.fptu.exe.skillswap.modules.payment.dto.request.PaymentCheckoutRequest;
import com.fptu.exe.skillswap.modules.payment.service.PaymentOrderService;
import com.fptu.exe.skillswap.modules.session.domain.SessionSourceType;
import com.fptu.exe.skillswap.modules.session.domain.SessionStatus;
import com.fptu.exe.skillswap.modules.session.repository.SessionRepository;
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
    private AcademicService academicService;

    @Autowired
    private CampusRepository campusRepository;

    @Autowired
    private AcademicProgramRepository academicProgramRepository;

    @Autowired
    private SpecializationRepository specializationRepository;

    @Autowired
    private MentorAvailabilityRuleRepository mentorAvailabilityRuleRepository;

    @Autowired
    private MentorAvailabilitySlotRepository mentorAvailabilitySlotRepository;

    @Autowired
    private AvailabilitySlotServiceRepository availabilitySlotServiceRepository;

    @Autowired
    private BookingService bookingService;

    @Autowired
    private com.fptu.exe.skillswap.modules.mentor.repository.MentorServiceRepository mentorServiceRepository;

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private ConversationParticipantRepository conversationParticipantRepository;

    @Autowired
    private PaymentOrderService paymentOrderService;

    @Autowired
    private com.fptu.exe.skillswap.modules.payment.service.CreditLedgerService creditLedgerService;

    private User menteeUser;
    private User mentorUser;
    private MentorProfile mentorProfile;
    private com.fptu.exe.skillswap.modules.mentor.domain.MentorService mentorService;

    @BeforeEach
    void setUp() {
        // Create Mentee
        menteeUser = userRepository.save(User.builder()
                .email("mentee-booking@test.com")
                .fullName("Mentee Booker")
                .status(UserStatus.ACTIVE)
                .build());
        completeAcademicProfile(menteeUser.getId(), "SE190001");
        creditLedgerService.issueCredit(
                menteeUser.getId(),
                com.fptu.exe.skillswap.modules.payment.domain.CreditOriginType.MANUAL,
                com.fptu.exe.skillswap.modules.payment.domain.LedgerSourceType.MANUAL,
                UUID.randomUUID(),
                1000,
                "Add test credit"
        );

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
                .headline("Spring Boot Mentor")
                .expertiseDescription("Support Java backend and database mentoring")
                .foundationSupportLevel(3)
                .outputReviewSupportLevel(3)
                .directionSupportLevel(2)
                .teachingMode(TeachingMode.ONLINE)
                .sessionDuration(60)
                .build());

        mentorService = mentorServiceRepository.saveAndFlush(
                com.fptu.exe.skillswap.modules.mentor.domain.MentorService.builder()
                        .mentorProfile(mentorProfile)
                        .title("Java Programming")
                        .description("Java basics")
                        .durationMinutes(60)
                        .isFree(false)
                        .priceScoin(100)
                        .isActive(true)
                        .build()
        );
    }

    @Test
    void testCompleteBookingFlow() {
        UUID mentorId = mentorUser.getId();
        UUID menteeId = menteeUser.getId();

        // 1. Mentor configures one availability window manually for the integration test
        LocalDate effectiveDate = LocalDate.now().plusDays(1);
        MentorAvailabilityRule rule = mentorAvailabilityRuleRepository.save(MentorAvailabilityRule.builder()
                .mentorProfile(mentorProfile)
                .ruleType(AvailabilityRuleType.OPEN)
                .repeatType(AvailabilityRepeatType.DAILY)
                .effectiveFrom(effectiveDate)
                .effectiveTo(effectiveDate.plusDays(2))
                .startTime(LocalTime.of(14, 0))
                .endTime(LocalTime.of(16, 0))
                .timezone("Asia/Ho_Chi_Minh")
                .active(true)
                .note("Daily afternoon slots")
                .build());

        MentorAvailabilitySlot slotToBook = mentorAvailabilitySlotRepository.saveAndFlush(MentorAvailabilitySlot.builder()
                .mentorProfile(mentorProfile)
                .rule(rule)
                .startTime(LocalDateTime.of(effectiveDate, LocalTime.of(14, 0)))
                .endTime(LocalDateTime.of(effectiveDate, LocalTime.of(16, 0)))
                .timezone("Asia/Ho_Chi_Minh")
                .isActive(true)
                .isBooked(false)
                .build());

        availabilitySlotServiceRepository.saveAndFlush(AvailabilitySlotService.builder()
                .id(new AvailabilitySlotServiceId(slotToBook.getId(), mentorService.getId()))
                .slot(slotToBook)
                .service(mentorService)
                .build());

        // 3. Mentee creates booking
        CreateBookingRequest createRequest = new CreateBookingRequest(
                slotToBook.getId(),
                mentorService.getId(),
                slotToBook.getStartTime(),
                slotToBook.getStartTime().plusMinutes(mentorService.getDurationMinutes()),
                "Need help with Java generics",
                "Wildcard boundaries explain"
        );

        BookingResponse booking = bookingService.createBooking(menteeId, createRequest);
        assertNotNull(booking);
        assertEquals(BookingStatus.PENDING, booking.status());
        assertEquals(booking.bookingId(), booking.sessionId());
        assertEquals(BookingStatus.PENDING, booking.sessionStatus());
        assertNull(booking.actualSessionId());
        assertNull(booking.actualSessionStatus());
        assertFalse(mentorAvailabilitySlotRepository.findById(slotToBook.getId()).orElseThrow().isBooked());
        assertTrue(sessionRepository.findBySourceTypeAndSourceId(SessionSourceType.BOOKING, booking.bookingId()).isEmpty());
        assertTrue(conversationRepository.findBySourceTypeAndSourceId(ConversationSourceType.BOOKING, booking.bookingId()).isEmpty());

        // 4. Mentor accepts booking
        BookingResponse accepted = bookingService.acceptBooking(
                mentorId, booking.bookingId(), new AcceptBookingRequest("Happy to help!")
        );
        assertEquals(BookingStatus.ACCEPTED_AWAITING_PAYMENT, accepted.status());
        assertEquals(accepted.bookingId(), accepted.sessionId());
        assertEquals(BookingStatus.ACCEPTED_AWAITING_PAYMENT, accepted.sessionStatus());
        assertNull(accepted.actualSessionId());
        assertNull(accepted.actualSessionStatus());
        assertTrue(mentorAvailabilitySlotRepository.findById(slotToBook.getId()).orElseThrow().isBooked());
        assertTrue(sessionRepository.findBySourceTypeAndSourceId(SessionSourceType.BOOKING, booking.bookingId()).isEmpty());
        assertTrue(conversationRepository.findBySourceTypeAndSourceId(ConversationSourceType.BOOKING, booking.bookingId()).isEmpty());

        paymentOrderService.checkout(menteeId, new PaymentCheckoutRequest(booking.bookingId(), null));

        BookingResponse paid = bookingService.getBookingDetail(menteeId, booking.bookingId());
        assertEquals(BookingStatus.PAID, paid.status());
        assertEquals(paid.bookingId(), paid.sessionId());
        assertEquals(BookingStatus.PAID, paid.sessionStatus());
        assertNotNull(paid.actualSessionId());
        assertEquals(SessionStatus.SCHEDULED, paid.actualSessionStatus());

        var session = sessionRepository.findBySourceTypeAndSourceId(SessionSourceType.BOOKING, booking.bookingId()).orElseThrow();
        assertEquals(paid.actualSessionId(), session.getId());
        assertEquals(SessionStatus.SCHEDULED, session.getStatus());
        assertEquals(slotToBook.getStartTime(), session.getScheduledStartTime());
        assertEquals(slotToBook.getStartTime().plusMinutes(mentorService.getDurationMinutes()), session.getScheduledEndTime());

        var conversation = conversationRepository.findBySourceTypeAndSourceId(ConversationSourceType.BOOKING, booking.bookingId()).orElseThrow();
        assertEquals(2, conversationParticipantRepository.findByConversationId(conversation.getId()).size());

        // 5. Mentor saves meeting link
        BookingResponse linked = bookingService.saveMeetingLink(
                mentorId, booking.bookingId(), new SaveMeetingLinkRequest(
                        MeetingPlatform.GOOGLE_MEET, "https://meet.google.com/abc-defg-hij", null
                )
        );
        assertEquals("https://meet.google.com/abc-defg-hij", linked.meetingLink());

        // 6. Mentor completes booking after meeting time
        // Modify booking selected/requested time to past so we can complete it
        com.fptu.exe.skillswap.modules.booking.domain.Booking dbBooking =
                ((com.fptu.exe.skillswap.modules.booking.repository.BookingRepository)
                        org.springframework.test.util.ReflectionTestUtils.getField(bookingService, "bookingRepository"))
                        .findById(booking.bookingId()).orElseThrow();
        dbBooking.setSelectedStartTime(LocalDateTime.now().minusHours(2));
        dbBooking.setSelectedEndTime(LocalDateTime.now().minusHours(1));
        dbBooking.setRequestedStartTime(LocalDateTime.now().minusHours(2));
        dbBooking.setRequestedEndTime(LocalDateTime.now().minusHours(1));

        BookingResponse mentorCompleted = bookingService.completeBooking(
                mentorId, booking.bookingId(), new CompleteBookingRequest("Good session, code works")
        );
        assertEquals(BookingStatus.AWAITING_MENTEE_CONFIRMATION, mentorCompleted.status());
        assertEquals(SessionStatus.COMPLETED, mentorCompleted.actualSessionStatus());

        BookingResponse completed = bookingService.completeBooking(
                menteeId, booking.bookingId(), new CompleteBookingRequest("Confirmed")
        );
        assertEquals(BookingStatus.COMPLETED, completed.status());
        assertEquals(SessionStatus.COMPLETED, completed.actualSessionStatus());
        assertNotNull(completed.completedAt());
    }

    private void completeAcademicProfile(UUID userId, String studentCode) {
        var campus = campusRepository.findAll().stream().findFirst().orElseThrow();
        var program = academicProgramRepository.findAll().stream().findFirst().orElseThrow();
        var specialization = specializationRepository.findByProgramIdAndIsActiveTrue(program.getId()).stream().findFirst().orElseThrow();

        academicService.updateStudentProfile(userId, StudentProfileRequest.builder()
                .studentCode(studentCode)
                .campusId(campus.getId())
                .programId(program.getId())
                .specializationId(specialization.getId())
                .semester(5)
                .intakeYear(2022)
                .isAlumni(false)
                .bio("Integration test profile")
                .build());
    }
}

