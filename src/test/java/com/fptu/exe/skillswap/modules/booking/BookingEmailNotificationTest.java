package com.fptu.exe.skillswap.modules.booking;

import com.fptu.exe.skillswap.modules.academic.dto.request.StudentProfileRequest;
import com.fptu.exe.skillswap.modules.academic.repository.AcademicProgramRepository;
import com.fptu.exe.skillswap.modules.academic.repository.CampusRepository;
import com.fptu.exe.skillswap.modules.academic.repository.SpecializationRepository;
import com.fptu.exe.skillswap.modules.academic.service.AcademicService;
import com.fptu.exe.skillswap.modules.booking.dto.request.AcceptBookingRequest;
import com.fptu.exe.skillswap.modules.booking.dto.request.CancelBookingRequest;
import com.fptu.exe.skillswap.modules.booking.dto.request.CreateBookingRequest;
import com.fptu.exe.skillswap.modules.booking.dto.request.ReplaceAvailabilitySlotServicesRequest;
import com.fptu.exe.skillswap.modules.booking.dto.request.RejectBookingRequest;
import com.fptu.exe.skillswap.modules.booking.dto.response.BookingResponse;
import com.fptu.exe.skillswap.modules.booking.domain.AvailabilityRepeatType;
import com.fptu.exe.skillswap.modules.booking.domain.AvailabilityRuleType;
import com.fptu.exe.skillswap.modules.booking.domain.AvailabilitySlotService;
import com.fptu.exe.skillswap.modules.booking.domain.AvailabilitySlotServiceId;
import com.fptu.exe.skillswap.modules.booking.domain.MentorAvailabilityRule;
import com.fptu.exe.skillswap.modules.booking.domain.MentorAvailabilitySlot;
import com.fptu.exe.skillswap.modules.booking.repository.MentorAvailabilityRuleRepository;
import com.fptu.exe.skillswap.modules.booking.repository.MentorAvailabilitySlotRepository;
import com.fptu.exe.skillswap.modules.booking.repository.AvailabilitySlotServiceRepository;
import com.fptu.exe.skillswap.modules.booking.service.BookingService;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.domain.UserStatus;
import com.fptu.exe.skillswap.modules.identity.repository.UserRepository;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorProfile;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorStatus;
import com.fptu.exe.skillswap.modules.mentor.domain.TeachingMode;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorProfileRepository;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorServiceRepository;
import com.fptu.exe.skillswap.modules.mail.service.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.*;

@SpringBootTest
@Transactional
class BookingEmailNotificationTest {

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
    private MentorServiceRepository mentorServiceRepository;

    @Autowired
    private AvailabilitySlotServiceRepository availabilitySlotServiceRepository;

    @MockBean
    private EmailService emailService;

    private User menteeUser;
    private User mentorUser;
    private MentorAvailabilitySlot testSlot;
    private com.fptu.exe.skillswap.modules.mentor.domain.MentorService mentorService;

    @BeforeEach
    void setUp() {
        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);
        menteeUser = createMentee("email-mentee-" + uniqueSuffix + "@test.com", "Email Mentee", "SE" + uniqueSuffix);
        
        mentorUser = userRepository.save(User.builder()
                .email("email-mentor-" + uniqueSuffix + "@test.com")
                .fullName("Email Mentor")
                .status(UserStatus.ACTIVE)
                .build());

        MentorProfile profile = mentorProfileRepository.save(MentorProfile.builder()
                .user(mentorUser)
                .status(MentorStatus.ACTIVE)
                .verifiedAt(LocalDateTime.now())
                .isAvailable(true)
                .headline("Email Mentor")
                .expertiseDescription("Support Email")
                .teachingMode(TeachingMode.ONLINE)
                .sessionDuration(60)
                .build());

        MentorAvailabilityRule availabilityRule = mentorAvailabilityRuleRepository.save(MentorAvailabilityRule.builder()
                .mentorProfile(profile)
                .ruleType(AvailabilityRuleType.OPEN)
                .repeatType(AvailabilityRepeatType.DAILY)
                .effectiveFrom(LocalDateTime.now().toLocalDate())
                .effectiveTo(LocalDateTime.now().toLocalDate().plusWeeks(2))
                .startTime(LocalDateTime.now().plusDays(2).toLocalTime().withMinute(0).withSecond(0).withNano(0))
                .endTime(LocalDateTime.now().plusDays(2).toLocalTime().withMinute(0).withSecond(0).withNano(0).plusHours(1))
                .timezone("Asia/Ho_Chi_Minh")
                .active(true)
                .build());

        LocalDateTime slotStart = LocalDateTime.now().plusDays(2)
                .withMinute(0)
                .withSecond(0)
                .withNano(0);
        testSlot = mentorAvailabilitySlotRepository.save(MentorAvailabilitySlot.builder()
                .mentorProfile(profile)
                .rule(availabilityRule)
                .startTime(slotStart)
                .endTime(slotStart.plusHours(1))
                .isActive(true)
                .isBooked(false)
                .build());

        mentorService = mentorServiceRepository.save(com.fptu.exe.skillswap.modules.mentor.domain.MentorService.builder()
                .mentorProfile(profile)
                .title("Email Mentoring")
                .description("Support Email")
                .durationMinutes(60)
                .isFree(true)
                .priceScoin(0)
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
    void acceptBooking_shouldPublishEmailEventAfterCommit() {
        BookingResponse booking = bookingService.createBooking(menteeUser.getId(), bookingRequest("T1", "D1"));
        
        bookingService.acceptBooking(mentorUser.getId(), booking.bookingId(), new AcceptBookingRequest("OK"));

        TestTransaction.flagForCommit();
        TestTransaction.end();

        verify(emailService, times(1)).sendSimpleEmail(
                eq(menteeUser.getEmail()),
                eq("[SkillSwap] Lịch mentoring của bạn đã được chấp nhận"),
                contains("đã chấp nhận lịch mentoring của bạn")
        );
    }

    @Test
    void rejectBooking_shouldPublishRejectedEmailEvent() {
        BookingResponse booking = bookingService.createBooking(menteeUser.getId(), bookingRequest("T1", "D1"));
        
        bookingService.rejectBooking(mentorUser.getId(), booking.bookingId(), new RejectBookingRequest("Busy", "Sorry"));

        TestTransaction.flagForCommit();
        TestTransaction.end();

        verify(emailService, times(1)).sendSimpleEmail(
                eq(menteeUser.getEmail()),
                eq("[SkillSwap] Yêu cầu đặt lịch của bạn đã bị từ chối"),
                contains("Busy")
        );
    }

    @Test
    void menteeCancelBooking_shouldPublishCancelledEmailToMentor() {
        BookingResponse booking = bookingService.createBooking(menteeUser.getId(), bookingRequest("T1", "D1"));
        
        bookingService.cancelBookingByMentee(menteeUser.getId(), booking.bookingId(), new CancelBookingRequest("Changed my mind"));

        TestTransaction.flagForCommit();
        TestTransaction.end();

        verify(emailService, times(1)).sendSimpleEmail(
                eq(mentorUser.getEmail()),
                eq("[SkillSwap] Mentee đã hủy lịch mentoring"),
                contains("Changed my mind")
        );
    }

    @Test
    void mentorCancelBooking_shouldPublishCancelledEmailToMentee() {
        BookingResponse booking = bookingService.createBooking(menteeUser.getId(), bookingRequest("T1", "D1"));
        bookingService.acceptBooking(mentorUser.getId(), booking.bookingId(), new AcceptBookingRequest("OK"));
        
        bookingService.cancelBookingByMentor(mentorUser.getId(), booking.bookingId(), new CancelBookingRequest("Emergency"));

        TestTransaction.flagForCommit();
        TestTransaction.end();

        verify(emailService, times(1)).sendSimpleEmail(
                eq(menteeUser.getEmail()),
                eq("[SkillSwap] Mentor đã hủy lịch mentoring"),
                contains("Emergency")
        );
    }

    @Test
    void emailSendFailure_shouldNotRollbackBooking() {
        doThrow(new RuntimeException("SMTP Connection Error")).when(emailService)
                .sendSimpleEmail(anyString(), anyString(), anyString());

        BookingResponse booking = bookingService.createBooking(menteeUser.getId(), bookingRequest("T1", "D1"));
        
        BookingResponse accepted = bookingService.acceptBooking(mentorUser.getId(), booking.bookingId(), new AcceptBookingRequest("OK"));

        TestTransaction.flagForCommit();
        TestTransaction.end();

        org.junit.jupiter.api.Assertions.assertEquals("ACCEPTED", accepted.status().name());
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
}

