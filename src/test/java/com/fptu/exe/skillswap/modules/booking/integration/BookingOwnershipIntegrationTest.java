package com.fptu.exe.skillswap.modules.booking.integration;

import com.fptu.exe.skillswap.modules.identity.dto.request.StudentProfileRequest;
import com.fptu.exe.skillswap.modules.identity.repository.AcademicProgramRepository;
import com.fptu.exe.skillswap.modules.identity.repository.CampusRepository;
import com.fptu.exe.skillswap.modules.identity.repository.SpecializationRepository;
import com.fptu.exe.skillswap.modules.identity.service.AcademicService;
import com.fptu.exe.skillswap.modules.booking.domain.AvailabilityRepeatType;
import com.fptu.exe.skillswap.modules.booking.domain.AvailabilityRuleType;
import com.fptu.exe.skillswap.modules.booking.domain.AvailabilitySlotService;
import com.fptu.exe.skillswap.modules.booking.domain.AvailabilitySlotServiceId;
import com.fptu.exe.skillswap.modules.booking.domain.MeetingPlatform;
import com.fptu.exe.skillswap.modules.booking.domain.MentorAvailabilityRule;
import com.fptu.exe.skillswap.modules.booking.domain.MentorAvailabilitySlot;
import com.fptu.exe.skillswap.modules.booking.dto.request.AcceptBookingRequest;
import com.fptu.exe.skillswap.modules.booking.dto.request.CreateBookingRequest;
import com.fptu.exe.skillswap.modules.booking.domain.BookingStatus;
import com.fptu.exe.skillswap.modules.booking.domain.BookingStateTestSupport;
import com.fptu.exe.skillswap.modules.booking.repository.BookingRepository;
import com.fptu.exe.skillswap.modules.booking.repository.AvailabilitySlotServiceRepository;
import com.fptu.exe.skillswap.modules.booking.repository.MentorAvailabilityRuleRepository;
import com.fptu.exe.skillswap.modules.booking.repository.MentorAvailabilitySlotRepository;
import com.fptu.exe.skillswap.modules.booking.service.BookingService;
import com.fptu.exe.skillswap.modules.chat.domain.ConversationSourceType;
import com.fptu.exe.skillswap.modules.chat.dto.request.SendMessageRequest;
import com.fptu.exe.skillswap.modules.chat.repository.ConversationRepository;
import com.fptu.exe.skillswap.modules.chat.repository.MessageRepository;
import com.fptu.exe.skillswap.modules.chat.service.ConversationService;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.domain.UserStatus;
import com.fptu.exe.skillswap.modules.identity.repository.UserRepository;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorProfile;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorStatus;
import com.fptu.exe.skillswap.modules.mentor.domain.TeachingMode;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorProfileRepository;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorServiceRepository;
import com.fptu.exe.skillswap.modules.payment.dto.request.PaymentCheckoutRequest;
import com.fptu.exe.skillswap.modules.payment.service.PaymentOrderService;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@Transactional
class BookingOwnershipIntegrationTest {

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
    private MentorServiceRepository mentorServiceRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private BookingService bookingService;

    @Autowired
    private ConversationService conversationService;

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private PaymentOrderService paymentOrderService;

    @Autowired
    private com.fptu.exe.skillswap.modules.payment.service.CreditLedgerService creditLedgerService;

    private User menteeUser;
    private User mentorUser;
    private User outsiderUser;
    private MentorProfile mentorProfile;
    private com.fptu.exe.skillswap.modules.mentor.domain.MentorService mentorService;

    @BeforeEach
    void setUp() {
        menteeUser = userRepository.save(User.builder()
                .email("ownership-mentee@test.com")
                .fullName("Ownership Mentee")
                .status(UserStatus.ACTIVE)
                .build());
        completeAcademicProfile(menteeUser.getId(), "SE290001");
        creditLedgerService.issueCredit(
                menteeUser.getId(),
                com.fptu.exe.skillswap.modules.payment.domain.CreditOriginType.MANUAL,
                com.fptu.exe.skillswap.modules.payment.domain.LedgerSourceType.MANUAL,
                UUID.randomUUID(),
                100_000,
                "Add test credit"
        );

        mentorUser = userRepository.save(User.builder()
                .email("ownership-mentor@test.com")
                .fullName("Ownership Mentor")
                .status(UserStatus.ACTIVE)
                .build());
        mentorProfile = mentorProfileRepository.save(MentorProfile.builder()
                .userId(mentorUser.getId())
                .status(MentorStatus.ACTIVE)
                .verifiedAt(LocalDateTime.now())
                .isAvailable(true)
                .headline("Ownership Mentor")
                .expertiseDescription("Ownership test mentor")
                .foundationSupportLevel(3)
                .outputReviewSupportLevel(3)
                .directionSupportLevel(2)
                .teachingMode(TeachingMode.ONLINE)
                .sessionDuration(60)
                .build());
        mentorService = mentorServiceRepository.saveAndFlush(
                com.fptu.exe.skillswap.modules.mentor.domain.MentorService.builder()
                        .mentorUserId(mentorProfile.getUserId())
                        .title("Ownership Service")
                        .description("Test ownership access")
                        .durationMinutes(60)
                        .isFree(false)
                        .priceScoin(72_000)
                        .isActive(true)
                        .build()
        );

        outsiderUser = userRepository.save(User.builder()
                .email("ownership-outsider@test.com")
                .fullName("Ownership Outsider")
                .status(UserStatus.ACTIVE)
                .build());
    }

    @Test
    void unrelatedUser_shouldNotAccessBookingOrConversation() {
        LocalDate effectiveDate = LocalDate.now().plusDays(1);
        MentorAvailabilityRule rule = mentorAvailabilityRuleRepository.save(MentorAvailabilityRule.builder()
                .mentorUserId(mentorProfile.getUserId())
                .ruleType(AvailabilityRuleType.OPEN)
                .repeatType(AvailabilityRepeatType.DAILY)
                .effectiveFrom(effectiveDate)
                .effectiveTo(effectiveDate.plusDays(1))
                .startTime(LocalTime.of(9, 0))
                .endTime(LocalTime.of(11, 0))
                .timezone("Asia/Ho_Chi_Minh")
                .active(true)
                .build());

        MentorAvailabilitySlot slot = mentorAvailabilitySlotRepository.saveAndFlush(MentorAvailabilitySlot.builder()
                .mentorUserId(mentorProfile.getUserId())
                .rule(rule)
                .startTime(LocalDateTime.of(effectiveDate, LocalTime.of(9, 0)))
                .endTime(LocalDateTime.of(effectiveDate, LocalTime.of(11, 0)))
                .timezone("Asia/Ho_Chi_Minh")
                .isActive(true)
                .isBooked(false)
                .build());

        availabilitySlotServiceRepository.saveAndFlush(AvailabilitySlotService.builder()
                .id(new AvailabilitySlotServiceId(slot.getId(), mentorService.getId()))
                .slot(slot)
                .build());

        var booking = bookingService.createBooking(menteeUser.getId(), new CreateBookingRequest(
                slot.getId(),
                mentorService.getId(),
                slot.getStartTime().toInstant(java.time.ZoneOffset.UTC),
                "Ownership help",
                "Please help with access control"
        ));

        bookingService.acceptBooking(mentorUser.getId(), booking.bookingId(),
                new AcceptBookingRequest("Accepted", MeetingPlatform.GOOGLE_MEET, "https://meet.google.com/test-abc", null));
        paymentOrderService.checkout(menteeUser.getId(), new PaymentCheckoutRequest(booking.bookingId(), null));

        var bookingEntity = bookingRepository.findById(booking.bookingId()).orElseThrow();
        BookingStateTestSupport.setStatus(bookingEntity, BookingStatus.PAID);
        bookingRepository.saveAndFlush(bookingEntity);
        conversationService.createDirectForAcceptedBooking(bookingEntity.getId(), mentorUser.getId(), menteeUser.getId());

        BaseException bookingException = assertThrows(BaseException.class,
                () -> bookingService.getBookingDetail(outsiderUser.getId(), booking.bookingId()));
        assertEquals(ErrorCode.UNAUTHORIZED, bookingException.getErrorCode());

        var conversation = conversationRepository.findBySourceTypeAndSourceId(ConversationSourceType.BOOKING, booking.bookingId())
                .orElseThrow();

        BaseException messageReadException = assertThrows(BaseException.class,
                () -> conversationService.getMessages(conversation.getId(), outsiderUser.getId(), null, 10));
        assertEquals(ErrorCode.ACCESS_DENIED, messageReadException.getErrorCode());

        BaseException sendMessageException = assertThrows(BaseException.class,
                () -> conversationService.sendMessage(conversation.getId(), outsiderUser.getId(), new SendMessageRequest("Intrude")));
        assertEquals(ErrorCode.ACCESS_DENIED, sendMessageException.getErrorCode());
    }

    private void completeAcademicProfile(UUID userId, String studentCode) {
        var campus = campusRepository.findAll().getFirst();
        var program = academicProgramRepository.findAll().getFirst();
        var specialization = specializationRepository.findByProgramIdAndIsActiveTrue(program.getId()).getFirst();

        academicService.updateStudentProfile(userId, StudentProfileRequest.builder()
                .studentCode(studentCode)
                .campusId(campus.getId())
                .programId(program.getId())
                .specializationId(specialization.getId())
                .semester(5)
                .intakeYear(2022)
                .isAlumni(false)
                .bio("Ownership integration profile")
                .build());
    }
}
