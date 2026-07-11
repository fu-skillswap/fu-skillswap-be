package com.fptu.exe.skillswap.modules.booking;

import com.fptu.exe.skillswap.modules.academic.dto.request.StudentProfileRequest;
import com.fptu.exe.skillswap.modules.academic.repository.AcademicProgramRepository;
import com.fptu.exe.skillswap.modules.academic.repository.CampusRepository;
import com.fptu.exe.skillswap.modules.academic.repository.SpecializationRepository;
import com.fptu.exe.skillswap.modules.academic.service.AcademicService;
import com.fptu.exe.skillswap.modules.booking.domain.BookingStatus;
import com.fptu.exe.skillswap.modules.booking.dto.request.AcceptBookingRequest;
import com.fptu.exe.skillswap.modules.booking.dto.request.CancelBookingRequest;
import com.fptu.exe.skillswap.modules.booking.dto.request.CreateBookingRequest;
import com.fptu.exe.skillswap.modules.booking.dto.request.RejectBookingRequest;
import com.fptu.exe.skillswap.modules.booking.dto.request.SaveMeetingLinkRequest;
import com.fptu.exe.skillswap.modules.booking.dto.response.BookingResponse;
import com.fptu.exe.skillswap.modules.booking.domain.AvailabilityRepeatType;
import com.fptu.exe.skillswap.modules.booking.domain.AvailabilityRuleType;
import com.fptu.exe.skillswap.modules.booking.domain.AvailabilitySlotService;
import com.fptu.exe.skillswap.modules.booking.domain.AvailabilitySlotServiceId;
import com.fptu.exe.skillswap.modules.booking.domain.MentorAvailabilityRule;
import com.fptu.exe.skillswap.modules.booking.domain.MeetingPlatform;
import com.fptu.exe.skillswap.modules.booking.domain.MentorAvailabilitySlot;
import com.fptu.exe.skillswap.modules.booking.repository.AvailabilitySlotServiceRepository;
import com.fptu.exe.skillswap.modules.booking.repository.MentorAvailabilityRuleRepository;
import com.fptu.exe.skillswap.modules.booking.repository.MentorAvailabilitySlotRepository;
import com.fptu.exe.skillswap.modules.booking.repository.BookingRepository;
import com.fptu.exe.skillswap.modules.booking.service.BookingService;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.domain.UserStatus;
import com.fptu.exe.skillswap.modules.identity.repository.UserRepository;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorProfile;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorStatus;
import com.fptu.exe.skillswap.modules.mentor.domain.TeachingMode;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorProfileRepository;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorServiceRepository;
import com.fptu.exe.skillswap.modules.notification.domain.Notification;
import com.fptu.exe.skillswap.modules.notification.domain.NotificationType;
import com.fptu.exe.skillswap.modules.notification.service.NotificationService;
import com.fptu.exe.skillswap.modules.payment.dto.request.PaymentCheckoutRequest;
import com.fptu.exe.skillswap.modules.payment.service.PaymentOrderService;
import com.fptu.exe.skillswap.shared.dto.response.PageResponse;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class BookingNotificationIntegrationTest {

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
    private BookingService bookingService;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private com.fptu.exe.skillswap.modules.notification.domain.NotificationRepository notificationRepository;

    @Autowired
    private PaymentOrderService paymentOrderService;

    @Autowired
    private com.fptu.exe.skillswap.modules.payment.service.CreditLedgerService creditLedgerService;

    @Autowired
    private MentorServiceRepository mentorServiceRepository;

    @Autowired
    private AvailabilitySlotServiceRepository availabilitySlotServiceRepository;

    private User mentee1;
    private User mentee2;
    private User mentee3;
    private User mentorUser;
    private MentorProfile mentorProfile;
    private MentorAvailabilitySlot testSlot;
    private com.fptu.exe.skillswap.modules.mentor.domain.MentorService mentorService;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @AfterEach
    void tearDown() {
        jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY FALSE");
        jdbcTemplate.execute("TRUNCATE TABLE bookings");
        jdbcTemplate.execute("TRUNCATE TABLE notifications");
        jdbcTemplate.execute("TRUNCATE TABLE credit_ledger_entries");
        jdbcTemplate.execute("TRUNCATE TABLE credit_ledger_accounts");
        jdbcTemplate.execute("TRUNCATE TABLE mentor_availability_slots");
        jdbcTemplate.execute("TRUNCATE TABLE mentor_availability_rules");
        jdbcTemplate.execute("TRUNCATE TABLE mentor_services");
        jdbcTemplate.execute("TRUNCATE TABLE student_profiles");
        jdbcTemplate.execute("TRUNCATE TABLE mentor_profiles");
        jdbcTemplate.execute("TRUNCATE TABLE user_roles");
        jdbcTemplate.execute("TRUNCATE TABLE users");
        jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY TRUE");
    }

    @BeforeEach
    void setUp() {
        mentee1 = createMentee("mentee1-noti@test.com", "Mentee One", "SE190001");
        mentee2 = createMentee("mentee2-noti@test.com", "Mentee Two", "SE190002");
        mentee3 = createMentee("mentee3-noti@test.com", "Mentee Three", "SE190003");

        creditLedgerService.issueCredit(mentee1.getId(), com.fptu.exe.skillswap.modules.payment.domain.CreditOriginType.MANUAL, com.fptu.exe.skillswap.modules.payment.domain.LedgerSourceType.MANUAL, UUID.randomUUID(), 100_000, "Test");
        creditLedgerService.issueCredit(mentee2.getId(), com.fptu.exe.skillswap.modules.payment.domain.CreditOriginType.MANUAL, com.fptu.exe.skillswap.modules.payment.domain.LedgerSourceType.MANUAL, UUID.randomUUID(), 100_000, "Test");
        creditLedgerService.issueCredit(mentee3.getId(), com.fptu.exe.skillswap.modules.payment.domain.CreditOriginType.MANUAL, com.fptu.exe.skillswap.modules.payment.domain.LedgerSourceType.MANUAL, UUID.randomUUID(), 100_000, "Test");

        mentorUser = userRepository.save(User.builder()
                .email("mentor-noti@test.com")
                .fullName("Mentor Noti")
                .status(UserStatus.ACTIVE)
                .build());

        mentorProfile = mentorProfileRepository.save(MentorProfile.builder()
                .user(mentorUser)
                .status(MentorStatus.ACTIVE)
                .verifiedAt(LocalDateTime.now())
                .isAvailable(true)
                .headline("Spring Boot Mentor")
                .expertiseDescription("Support Java")
                .foundationSupportLevel(3)
                .outputReviewSupportLevel(3)
                .directionSupportLevel(2)
                .teachingMode(TeachingMode.ONLINE)
                .sessionDuration(60)
                .build());

        MentorAvailabilityRule availabilityRule = mentorAvailabilityRuleRepository.save(MentorAvailabilityRule.builder()
                .mentorProfile(mentorProfile)
                .ruleType(AvailabilityRuleType.OPEN)
                .repeatType(AvailabilityRepeatType.DAILY)
                .effectiveFrom(LocalDateTime.now().toLocalDate())
                .effectiveTo(LocalDateTime.now().toLocalDate().plusWeeks(2))
                .startTime(LocalDateTime.now().plusDays(2).toLocalTime().withMinute(0).withSecond(0).withNano(0))
                .endTime(LocalDateTime.now().plusDays(2).toLocalTime().withMinute(0).withSecond(0).withNano(0).plusHours(1))
                .timezone("Asia/Ho_Chi_Minh")
                .active(true)
                .build());

        testSlot = mentorAvailabilitySlotRepository.saveAndFlush(MentorAvailabilitySlot.builder()
                .mentorProfile(mentorProfile)
                .rule(availabilityRule)
                .startTime(LocalDateTime.now().plusDays(2))
                .endTime(LocalDateTime.now().plusDays(2).plusHours(1))
                .isActive(true)
                .isBooked(false)
                .build());

        mentorService = mentorServiceRepository.saveAndFlush(com.fptu.exe.skillswap.modules.mentor.domain.MentorService.builder()
                .mentorProfile(mentorProfile)
                .title("Java Mentoring")
                .description("Support Java backend and REST API")
                .durationMinutes(60)
                .isFree(false)
                .priceScoin(72_000)
                .isActive(true)
                .build());

        availabilitySlotServiceRepository.saveAndFlush(AvailabilitySlotService.builder()
                .id(new AvailabilitySlotServiceId(testSlot.getId(), mentorService.getId()))
                .slot(testSlot)
                .service(mentorService)
                .build());
    }

    private User createMentee(String email, String name, String code) {
        User user = userRepository.save(User.builder()
                .email(email)
                .fullName(name)
                .status(UserStatus.ACTIVE)
                .build());
        
        var campus = campusRepository.findAll().stream().findFirst().orElseThrow();
        var program = academicProgramRepository.findAll().stream().findFirst().orElseThrow();
        var specialization = specializationRepository.findByProgramIdAndIsActiveTrue(program.getId()).stream().findFirst().orElseThrow();

        academicService.updateStudentProfile(user.getId(), StudentProfileRequest.builder()
                .studentCode(code)
                .campusId(campus.getId())
                .programId(program.getId())
                .specializationId(specialization.getId())
                .semester(5)
                .intakeYear(2022)
                .isAlumni(false)
                .bio("Integration test profile")
                .build());
        return user;
    }

    @Test
    void createBooking_shouldNotifyMentor() {
        CreateBookingRequest req = bookingRequest("Help", "Desc");
        BookingResponse booking = bookingService.createBooking(mentee1.getId(), req);
        commitTransaction();
        awaitAsyncNotifications();

        var notis = notificationService.getMyNotifications(mentorUser.getId(), true, PageRequest.of(0, 10)).getContent();
        assertEquals(1, notis.size());
        assertEquals(NotificationType.BOOKING_REQUEST_CREATED.name(), notis.get(0).getType());
        assertEquals("BOOKING", notis.get(0).getRelatedEntityType());
        assertEquals(booking.bookingId(), notis.get(0).getRelatedEntityId());
        assertFalse(notis.get(0).isRead());
    }

    @Test
    void acceptBooking_shouldNotifyAcceptedMentee_andAutoRejectedSiblingMentees() {
        // 3 Mentees request same slot
        BookingResponse b1 = bookingService.createBooking(mentee1.getId(), bookingRequest("T1", "D1"));
        BookingResponse b2 = bookingService.createBooking(mentee2.getId(), bookingRequest("T2", "D2"));
        BookingResponse b3 = bookingService.createBooking(mentee3.getId(), bookingRequest("T3", "D3"));

        // Mentor accepts b1
        bookingService.acceptBooking(mentorUser.getId(), b1.bookingId(), new AcceptBookingRequest("OK"));
        commitTransaction();
        awaitAsyncNotifications();

        // Mentee1 should get ACCEPTED
        var mentee1Notis = notificationService.getMyNotifications(mentee1.getId(), true, PageRequest.of(0, 10)).getContent();
        assertEquals(1, mentee1Notis.size());
        assertEquals(NotificationType.BOOKING_ACCEPTED.name(), mentee1Notis.get(0).getType());
        assertEquals(b1.bookingId(), mentee1Notis.get(0).getRelatedEntityId());

        // Mentee2 should get AUTO_REJECTED
        var mentee2Notis = notificationService.getMyNotifications(mentee2.getId(), true, PageRequest.of(0, 10)).getContent();
        assertEquals(1, mentee2Notis.size());
        assertEquals(NotificationType.BOOKING_AUTO_REJECTED.name(), mentee2Notis.get(0).getType());
        assertEquals(b2.bookingId(), mentee2Notis.get(0).getRelatedEntityId());

        // Mentee3 should get AUTO_REJECTED
        var mentee3Notis = notificationService.getMyNotifications(mentee3.getId(), true, PageRequest.of(0, 10)).getContent();
        assertEquals(1, mentee3Notis.size());
        assertEquals(NotificationType.BOOKING_AUTO_REJECTED.name(), mentee3Notis.get(0).getType());
        assertEquals(b3.bookingId(), mentee3Notis.get(0).getRelatedEntityId());

        // Mentor should NOT get any of these (only the 3 original REQUEST_CREATED)
        var mentorNotis = notificationService.getMyNotifications(mentorUser.getId(), true, PageRequest.of(0, 10)).getContent();
        assertEquals(3, mentorNotis.size());
        assertTrue(mentorNotis.stream().allMatch(n -> n.getType().equals(NotificationType.BOOKING_REQUEST_CREATED.name())));
    }

    @Test
    void rejectBooking_shouldNotifyMentee() {
        BookingResponse b1 = bookingService.createBooking(mentee1.getId(), bookingRequest("T1", "D1"));
        commitTransaction();
        awaitAsyncNotifications();

        long unreadBefore = notificationService.getMyUnreadCount(mentee1.getId());
        bookingService.rejectBooking(mentorUser.getId(), b1.bookingId(), new RejectBookingRequest("No time", "Sorry"));
        commitTransaction();
        awaitAsyncNotifications();

        var menteeNotis = notificationService.getMyNotifications(mentee1.getId(), true, PageRequest.of(0, 10)).getContent();
        assertEquals(unreadBefore + 1, menteeNotis.size());
        assertTrue(menteeNotis.stream().anyMatch(n ->
                NotificationType.BOOKING_REJECTED.name().equals(n.getType())
                        && b1.bookingId().equals(n.getRelatedEntityId())
        ));
    }

    @Test
    void menteeCancelBooking_shouldNotifyMentor() {
        BookingResponse b1 = bookingService.createBooking(mentee1.getId(), bookingRequest("T1", "D1"));
        commitTransaction();
        awaitAsyncNotifications();

        // Before cancel, mentor has 1 request created noti
        long unreadBefore = notificationService.getMyUnreadCount(mentorUser.getId());
        
        bookingService.cancelBookingByMentee(mentee1.getId(), b1.bookingId(), new CancelBookingRequest("Change plan"));
        commitTransaction();
        awaitAsyncNotifications();
        
        var mentorNotis = notificationService.getMyNotifications(mentorUser.getId(), true, PageRequest.of(0, 10)).getContent();
        assertEquals(unreadBefore + 1, mentorNotis.size());
        assertTrue(mentorNotis.stream().anyMatch(n ->
                NotificationType.BOOKING_CANCELLED_BY_MENTEE.name().equals(n.getType())
                        && b1.bookingId().equals(n.getRelatedEntityId())
        ));
    }

    @Test
    void mentorCancelBooking_shouldNotifyMentee() {
        BookingResponse b1 = bookingService.createBooking(mentee1.getId(), bookingRequest("T1", "D1"));
        bookingService.acceptBooking(mentorUser.getId(), b1.bookingId(), new AcceptBookingRequest("OK"));
        commitTransaction();
        awaitAsyncNotifications();

        // Before cancel, mentee has 1 accepted noti
        long unreadBefore = notificationService.getMyUnreadCount(mentee1.getId());
        
        bookingService.cancelBookingByMentor(mentorUser.getId(), b1.bookingId(), new CancelBookingRequest("Sick"));
        commitTransaction();
        awaitAsyncNotifications();
        
        var menteeNotis = notificationService.getMyNotifications(mentee1.getId(), true, PageRequest.of(0, 10)).getContent();
        assertEquals(unreadBefore + 1, menteeNotis.size());
        assertTrue(menteeNotis.stream().anyMatch(n ->
                NotificationType.BOOKING_CANCELLED_BY_MENTOR.name().equals(n.getType())
                        && b1.bookingId().equals(n.getRelatedEntityId())
        ));
    }

    @Test
    void updateMeetingLink_shouldNotifyMentee() {
        BookingResponse b1 = bookingService.createBooking(mentee1.getId(), bookingRequest("T1", "D1"));
        bookingService.acceptBooking(mentorUser.getId(), b1.bookingId(), new AcceptBookingRequest("OK"));
        paymentOrderService.checkout(mentee1.getId(), new PaymentCheckoutRequest(b1.bookingId(), null));
        
        var bookingEntity = bookingRepository.findById(b1.bookingId()).orElseThrow();
        bookingEntity.setStatus(BookingStatus.PAID);
        bookingRepository.saveAndFlush(bookingEntity);

        commitTransaction();
        awaitAsyncNotifications();

        long unreadBefore = notificationService.getMyUnreadCount(mentee1.getId());
        
        bookingService.saveMeetingLink(mentorUser.getId(), b1.bookingId(), new SaveMeetingLinkRequest(MeetingPlatform.GOOGLE_MEET, "https://meet.google.com/test", null));
        commitTransaction();
        awaitAsyncNotifications();
        
        var menteeNotis = notificationService.getMyNotifications(mentee1.getId(), true, PageRequest.of(0, 10)).getContent();
        assertEquals(unreadBefore + 1, menteeNotis.size());
        assertTrue(menteeNotis.stream().anyMatch(n ->
                NotificationType.MEETING_LINK_UPDATED.name().equals(n.getType())
                        && b1.bookingId().equals(n.getRelatedEntityId())
        ));
    }

    @Test
    void failedBookingAction_shouldNotCreateNotification() {
        // Create booking successfully
        BookingResponse b1 = bookingService.createBooking(mentee1.getId(), bookingRequest("T1", "D1"));
        long menteeNotisCount = notificationService.getMyUnreadCount(mentee1.getId());
        
        // Try invalid reject (unauthorized mentee tries to reject their own booking as a mentor)
        assertThrows(BaseException.class, () -> bookingService.rejectBooking(mentee1.getId(), b1.bookingId(), new RejectBookingRequest("Hack", "")));
        
        // Assert no notification was added
        assertEquals(menteeNotisCount, notificationService.getMyUnreadCount(mentee1.getId()));
    }

    private CreateBookingRequest bookingRequest(String title, String description) {
        return new CreateBookingRequest(
                testSlot.getId(),
                mentorService.getId(),
                testSlot.getStartTime(),
                testSlot.getStartTime().plusMinutes(mentorService.getDurationMinutes()),
                title,
                description
        );
    }

    private void awaitAsyncNotifications() {
        try {
            Thread.sleep(150);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void commitTransaction() {
        if (org.springframework.test.context.transaction.TestTransaction.isActive()) {
            org.springframework.test.context.transaction.TestTransaction.flagForCommit();
            org.springframework.test.context.transaction.TestTransaction.end();
            org.springframework.test.context.transaction.TestTransaction.start();
        }
    }
}

