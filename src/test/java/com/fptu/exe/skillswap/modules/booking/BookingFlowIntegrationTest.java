package com.fptu.exe.skillswap.modules.booking;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fptu.exe.skillswap.modules.academic.domain.AcademicProgram;
import com.fptu.exe.skillswap.modules.academic.domain.Campus;
import com.fptu.exe.skillswap.modules.academic.domain.Specialization;
import com.fptu.exe.skillswap.modules.academic.domain.StudentProfile;
import com.fptu.exe.skillswap.modules.academic.repository.AcademicProgramRepository;
import com.fptu.exe.skillswap.modules.academic.repository.CampusRepository;
import com.fptu.exe.skillswap.modules.academic.repository.SpecializationRepository;
import com.fptu.exe.skillswap.modules.academic.repository.StudentProfileRepository;
import com.fptu.exe.skillswap.modules.booking.domain.Booking;
import com.fptu.exe.skillswap.modules.booking.domain.BookingStatus;
import com.fptu.exe.skillswap.modules.booking.domain.MentorAvailabilitySlot;
import com.fptu.exe.skillswap.modules.booking.domain.Session;
import com.fptu.exe.skillswap.modules.booking.domain.SessionStatus;
import com.fptu.exe.skillswap.modules.booking.repository.BookingRepository;
import com.fptu.exe.skillswap.modules.booking.repository.MentorAvailabilitySlotRepository;
import com.fptu.exe.skillswap.modules.booking.repository.SessionRepository;
import com.fptu.exe.skillswap.modules.catalog.domain.MentorTag;
import com.fptu.exe.skillswap.modules.catalog.domain.MentorTagId;
import com.fptu.exe.skillswap.modules.catalog.domain.MentorTagType;
import com.fptu.exe.skillswap.modules.catalog.domain.Tag;
import com.fptu.exe.skillswap.modules.catalog.domain.TagStatus;
import com.fptu.exe.skillswap.modules.catalog.domain.TagType;
import com.fptu.exe.skillswap.modules.catalog.repository.MentorTagRepository;
import com.fptu.exe.skillswap.modules.catalog.repository.TagRepository;
import com.fptu.exe.skillswap.modules.feedback.domain.SessionFeedback;
import com.fptu.exe.skillswap.modules.feedback.repository.SessionFeedbackRepository;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.domain.UserRole;
import com.fptu.exe.skillswap.modules.identity.domain.UserRoleId;
import com.fptu.exe.skillswap.modules.identity.repository.UserRepository;
import com.fptu.exe.skillswap.modules.identity.repository.UserRoleRepository;
import com.fptu.exe.skillswap.modules.identity.service.GoogleAuthService;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorProfile;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorService;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorStatus;
import com.fptu.exe.skillswap.modules.mentor.domain.TeachingMode;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorProfileRepository;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorServiceRepository;
import com.fptu.exe.skillswap.shared.constant.RoleCode;
import org.junit.jupiter.api.Test;
import com.fptu.exe.skillswap.modules.booking.service.BookingService;
import com.fptu.exe.skillswap.modules.booking.dto.CreateBookingRequest;
import com.fptu.exe.skillswap.modules.booking.dto.BookingResponse;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class BookingFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CampusRepository campusRepository;

    @Autowired
    private AcademicProgramRepository academicProgramRepository;

    @Autowired
    private SpecializationRepository specializationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private StudentProfileRepository studentProfileRepository;

    @Autowired
    private MentorProfileRepository mentorProfileRepository;

    @Autowired
    private TagRepository tagRepository;

    @Autowired
    private MentorTagRepository mentorTagRepository;

    @Autowired
    private MentorServiceRepository mentorServiceRepository;

    @Autowired
    private MentorAvailabilitySlotRepository mentorAvailabilitySlotRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private SessionFeedbackRepository sessionFeedbackRepository;

    @Autowired
    private BookingService bookingService;

    @MockBean
    private GoogleAuthService googleAuthService;

    @Test
    void bigBangFlow_discoveryCreateBookingAcceptReject_shouldSucceed() throws Exception {
        String nonce = UUID.randomUUID().toString().substring(0, 8);
        String menteeEmail = "mentee-booking-" + nonce + "@fpt.edu.vn";
        String mentorEmail = "mentor-booking-" + nonce + "@fpt.edu.vn";

        mockGoogleLogin("mentee-id-token-" + nonce, menteeEmail, "google-mentee-" + nonce, "Mentee " + nonce);
        mockGoogleLogin("mentor-id-token-" + nonce, mentorEmail, "google-mentor-" + nonce, "Mentor " + nonce);

        String menteeAccessToken = loginAndExtractAccessToken("mentee-id-token-" + nonce);
        String initialMentorAccessToken = loginAndExtractAccessToken("mentor-id-token-" + nonce);
        assertThat(initialMentorAccessToken).isNotBlank();

        User mentee = userRepository.findByEmail(menteeEmail).orElseThrow();
        User mentorUser = userRepository.findByEmail(mentorEmail).orElseThrow();

        AcademicContext academicContext = resolveAcademicContext();
        upsertStudentProfile(menteeAccessToken, academicContext, generateStudentCode(11), 5, false);
        upsertStudentProfile(initialMentorAccessToken, academicContext, generateStudentCode(22), 8, false);
        mentee = userRepository.findByEmail(menteeEmail).orElseThrow();
        mentorUser = userRepository.findByEmail(mentorEmail).orElseThrow();

        userRoleRepository.save(UserRole.builder()
                .id(new UserRoleId(mentorUser.getId(), RoleCode.MENTOR))
                .user(mentorUser)
                .assignedBy(mentorUser)
                .build());

        Tag expertiseTag = createTag("JAVA_BACKEND_" + nonce, "Java Backend", TagType.TECH_SKILL);
        Tag helpTopicTag = createTag("CV_REVIEW_" + nonce, "CV Review", TagType.HELP_TOPIC);

        upsertMentorProfile(initialMentorAccessToken, expertiseTag.getId(), helpTopicTag.getId());

        MentorProfile mentorProfile = mentorProfileRepository.findById(mentorUser.getId()).orElseThrow();
        mentorProfile.setStatus(MentorStatus.ACTIVE);
        mentorProfile.setTeachingMode(TeachingMode.ONLINE);
        mentorProfile.setSessionDuration(60);
        mentorProfile.setAverageRating(new BigDecimal("5.00"));
        mentorProfile.setTotalReviews(1);
        mentorProfile.setTotalCompletedSessions(200);
        mentorProfile.setVerifiedAt(LocalDateTime.now().minusDays(10));
        mentorProfile.setAvailable(true);
        mentorProfile = mentorProfileRepository.save(mentorProfile);

        MentorService mentorService = createMentorService(mentorProfile);
        MentorAvailabilitySlot firstSlot = createFutureSlot(mentorProfile, 2, 9);
        MentorAvailabilitySlot secondSlot = createFutureSlot(mentorProfile, 3, 14);
        createPublicReviewEvidence(mentorProfile, nonce);

        String mentorAccessToken = loginAndExtractAccessToken("mentor-id-token-" + nonce);

        mockMvc.perform(get("/api/mentors/recommendations")
                        .header("Authorization", bearer(menteeAccessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].mentor.mentorUserId").value(mentorUser.getId().toString()));

        mockMvc.perform(get("/api/mentors")
                        .header("Authorization", bearer(menteeAccessToken))
                        .param("keyword", "Java")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].mentorUserId").value(mentorUser.getId().toString()));

        mockMvc.perform(get("/api/mentors/{mentorUserId}", mentorUser.getId())
                        .header("Authorization", bearer(menteeAccessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.displayName").value(mentorUser.getFullName()))
                .andExpect(jsonPath("$.data.services[0].title").value(mentorService.getTitle()));

        mockMvc.perform(get("/api/mentors/{mentorUserId}/reviews", mentorUser.getId())
                        .header("Authorization", bearer(menteeAccessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].rating").value(5));

        mockMvc.perform(get("/api/mentors/{mentorUserId}/availability", mentorUser.getId())
                        .header("Authorization", bearer(menteeAccessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));

        MvcResult createBookingResult = mockMvc.perform(post("/api/bookings")
                        .header("Authorization", bearer(menteeAccessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "mentorUserId": "%s",
                                  "availabilitySlotId": "%s",
                                  "serviceId": "%s",
                                  "learningGoalTitle": "Cần định hướng Java Backend",
                                  "learningGoalDescription": "Muốn được mentor review CV và góp ý lộ trình"
                                }
                                """.formatted(mentorUser.getId(), firstSlot.getId(), mentorService.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.mentorDisplayName").value(mentorUser.getFullName()))
                .andReturn();

        String firstBookingId = extractPath(createBookingResult, "data.bookingId");
        assertThat(firstBookingId).isNotBlank();

        mockMvc.perform(get("/api/me/bookings")
                        .header("Authorization", bearer(menteeAccessToken))
                        .param("role", "MENTEE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].bookingId").value(firstBookingId))
                .andExpect(jsonPath("$.data.content[0].status").value("PENDING"));

        mockMvc.perform(get("/api/me/bookings")
                        .header("Authorization", bearer(mentorAccessToken))
                        .param("role", "MENTOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].bookingId").value(firstBookingId));

        mockMvc.perform(post("/api/mentor/bookings/{bookingId}/accept", firstBookingId)
                        .header("Authorization", bearer(mentorAccessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "mentorResponseNote": "Anh đã nhận lịch, hẹn em đúng giờ"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.data.sessionId").isNotEmpty())
                .andExpect(jsonPath("$.data.sessionStatus").value("SCHEDULED"));

        mockMvc.perform(get("/api/me/bookings/{bookingId}", firstBookingId)
                        .header("Authorization", bearer(menteeAccessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.data.sessionId").isNotEmpty());

        MvcResult secondBookingResult = mockMvc.perform(post("/api/bookings")
                        .header("Authorization", bearer(menteeAccessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "mentorUserId": "%s",
                                  "availabilitySlotId": "%s",
                                  "learningGoalTitle": "Cần hỏi thêm về internship",
                                  "learningGoalDescription": "Muốn hỏi về cách chuẩn bị phỏng vấn"
                                }
                                """.formatted(mentorUser.getId(), secondSlot.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andReturn();

        String secondBookingId = extractPath(secondBookingResult, "data.bookingId");

        mockMvc.perform(post("/api/mentor/bookings/{bookingId}/reject", secondBookingId)
                        .header("Authorization", bearer(mentorAccessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "rejectReason": "Khung giờ này mentor có việc đột xuất",
                                  "mentorResponseNote": "Em vui lòng chọn slot khác giúp anh"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"))
                .andExpect(jsonPath("$.data.rejectReason").value("Khung giờ này mentor có việc đột xuất"));

        mockMvc.perform(get("/api/me/bookings/{bookingId}", secondBookingId)
                        .header("Authorization", bearer(menteeAccessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"));

        mockMvc.perform(get("/api/mentors/{mentorUserId}/availability", mentorUser.getId())
                        .header("Authorization", bearer(menteeAccessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].slotId").value(secondSlot.getId().toString()));

        Booking acceptedBooking = bookingRepository.findById(UUID.fromString(firstBookingId)).orElseThrow();
        Booking rejectedBooking = bookingRepository.findById(UUID.fromString(secondBookingId)).orElseThrow();
        Session acceptedSession = sessionRepository.findByBookingId(acceptedBooking.getId()).orElseThrow();
        MentorAvailabilitySlot refreshedFirstSlot = mentorAvailabilitySlotRepository.findById(firstSlot.getId()).orElseThrow();
        MentorAvailabilitySlot refreshedSecondSlot = mentorAvailabilitySlotRepository.findById(secondSlot.getId()).orElseThrow();

        assertThat(acceptedBooking.getStatus()).isEqualTo(BookingStatus.ACCEPTED);
        assertThat(acceptedSession.getStatus()).isEqualTo(SessionStatus.SCHEDULED);
        assertThat(rejectedBooking.getStatus()).isEqualTo(BookingStatus.REJECTED);
        assertThat(refreshedFirstSlot.isBooked()).isTrue();
        assertThat(refreshedSecondSlot.isBooked()).isFalse();
    }

    private void mockGoogleLogin(String idToken, String email, String sub, String name) {
        GoogleAuthService.GoogleUserInfo googleUserInfo = new GoogleAuthService.GoogleUserInfo();
        googleUserInfo.setEmail(email);
        googleUserInfo.setSub(sub);
        googleUserInfo.setName(name);
        googleUserInfo.setPicture("https://example.com/" + sub + ".jpg");
        googleUserInfo.setEmail_verified("true");
        when(googleAuthService.verifyToken(idToken)).thenReturn(googleUserInfo);
    }

    private String loginAndExtractAccessToken(String idToken) throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/api/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "idToken": "%s"
                                }
                                """.formatted(idToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andReturn();

        return extractPath(loginResult, "data.accessToken");
    }

    private AcademicContext resolveAcademicContext() {
        Campus campus = campusRepository.findByIsActiveTrue().stream()
                .min(Comparator.comparing(Campus::getName))
                .orElseThrow();
        AcademicProgram program = academicProgramRepository.findByIsActiveTrue().stream()
                .min(Comparator.comparing(AcademicProgram::getCode))
                .orElseThrow();
        Specialization specialization = specializationRepository.findByProgramIdAndIsActiveTrue(program.getId()).stream()
                .findFirst()
                .orElseThrow();
        return new AcademicContext(campus, program, specialization);
    }

    private void upsertStudentProfile(
            String accessToken,
            AcademicContext academicContext,
            String studentCode,
            int semester,
            boolean alumni
    ) throws Exception {
        mockMvc.perform(put("/api/me/student-profile")
                        .header("Authorization", bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "studentCode": "%s",
                                  "displayName": "Booking Flow User",
                                  "avatarUrl": "https://example.com/avatar-booking-flow.jpg",
                                  "campusId": "%s",
                                  "programId": "%s",
                                  "specializationId": "%s",
                                  "semester": %d,
                                  "intakeYear": 2022,
                                  "isAlumni": %s,
                                  "bio": "Integration booking flow profile"
                                }
                                """.formatted(
                                studentCode,
                                academicContext.campus().getId(),
                                academicContext.program().getId(),
                                academicContext.specialization().getId(),
                                semester,
                                alumni
                        )))
                .andExpect(status().isOk());
    }

    private void upsertMentorProfile(String accessToken, UUID expertiseTagId, UUID helpTopicTagId) throws Exception {
        mockMvc.perform(put("/api/me/mentor-profile")
                        .header("Authorization", bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "headline": "Java Backend Mentor",
                                  "expertiseDescription": "Có kinh nghiệm Spring Boot và PostgreSQL",
                                  "supportingSubjects": "Cơ sở dữ liệu, Lập trình Java",
                                  "isAvailable": true,
                                  "helpTopicIds": ["%s"],
                                  "teachingMode": "ONLINE",
                                  "sessionDuration": 60,
                                  "linkedinUrl": "https://linkedin.com/in/mentor-demo",
                                  "githubUrl": "https://github.com/mentor-demo",
                                  "portfolioUrl": "https://portfolio.example.com/mentor-demo"
                                }
                                """.formatted(helpTopicTagId)))
                .andExpect(status().isOk());
    }

    private Tag createTag(String code, String nameVi, TagType tagType) {
        return tagRepository.save(Tag.builder()
                .code(code)
                .nameVi(nameVi)
                .nameEn(nameVi)
                .type(tagType)
                .status(TagStatus.ACTIVE)
                .build());
    }

    private MentorService createMentorService(MentorProfile mentorProfile) {
        return mentorServiceRepository.save(MentorService.builder()
                .mentorProfile(mentorProfile)
                .title("CV Review và Mock Interview")
                .description("Review CV, góp ý LinkedIn và hỏi đáp phỏng vấn internship")
                .durationMinutes(60)
                .isFree(false)
                .priceAmount(new BigDecimal("120000"))
                .currency("VND")
                .isActive(true)
                .build());
    }

    private MentorAvailabilitySlot createFutureSlot(MentorProfile mentorProfile, int plusDays, int startHour) {
        LocalDateTime start = LocalDateTime.now().plusDays(plusDays).withHour(startHour).withMinute(0).withSecond(0).withNano(0);
        return mentorAvailabilitySlotRepository.save(MentorAvailabilitySlot.builder()
                .mentorProfile(mentorProfile)
                .startTime(start)
                .endTime(start.plusHours(1))
                .timezone("Asia/Ho_Chi_Minh")
                .isBooked(false)
                .isActive(true)
                .build());
    }

    private void createPublicReviewEvidence(MentorProfile mentorProfile, String nonce) {
        User reviewer = userRepository.save(User.builder()
                .email("reviewer-" + nonce + "@fpt.edu.vn")
                .fullName("Reviewer " + nonce)
                .avatarUrl("https://example.com/reviewer-" + nonce + ".jpg")
                .build());
        userRoleRepository.save(UserRole.builder()
                .id(new UserRoleId(reviewer.getId(), RoleCode.MENTEE))
                .user(reviewer)
                .assignedBy(reviewer)
                .build());

        MentorAvailabilitySlot historicalSlot = mentorAvailabilitySlotRepository.save(MentorAvailabilitySlot.builder()
                .mentorProfile(mentorProfile)
                .startTime(LocalDateTime.now().minusDays(10))
                .endTime(LocalDateTime.now().minusDays(10).plusHours(1))
                .timezone("Asia/Ho_Chi_Minh")
                .isBooked(true)
                .isActive(false)
                .build());

        Booking historicalBooking = bookingRepository.save(Booking.builder()
                .mentee(reviewer)
                .mentorProfile(mentorProfile)
                .slot(historicalSlot)
                .status(BookingStatus.COMPLETED)
                .learningGoalTitle("Review historical booking")
                .learningGoalDescription("Used for seeding public review")
                .requestedStartTime(historicalSlot.getStartTime())
                .requestedEndTime(historicalSlot.getEndTime())
                .completedAt(LocalDateTime.now().minusDays(9))
                .build());

        Session historicalSession = sessionRepository.save(Session.builder()
                .booking(historicalBooking)
                .status(SessionStatus.COMPLETED)
                .actualStartTime(historicalSlot.getStartTime())
                .actualEndTime(historicalSlot.getEndTime())
                .build());

        sessionFeedbackRepository.save(SessionFeedback.builder()
                .session(historicalSession)
                .reviewer(reviewer)
                .reviewee(mentorProfile.getUser())
                .rating(5)
                .comment("Mentor giải thích rất rõ và thực tế")
                .wouldRecommend(true)
                .isPublic(true)
                .build());
    }

    private String extractPath(MvcResult result, String path) throws Exception {
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        for (String part : path.split("\\.")) {
            node = node.path(part);
        }
        return node.asText();
    }

    private String bearer(String accessToken) {
        return "Bearer " + accessToken;
    }

    private String generateStudentCode(int suffixSeed) {
        int khóa = (suffixSeed % 22) + 1;
        int suffix = ((suffixSeed * 379) % 9000) + 1000;
        return "SE" + String.format("%02d%04d", khóa, suffix);
    }

    private record AcademicContext(
            Campus campus,
            AcademicProgram program,
            Specialization specialization
    ) {
    }

    @Test
    void createBooking_concurrentRequests_shouldOnlyAllowOneBooking() throws Exception {
        String nonce = UUID.randomUUID().toString().substring(0, 8);
        String mentee1Email = "mentee1-concurrency-" + nonce + "@fpt.edu.vn";
        String mentee2Email = "mentee2-concurrency-" + nonce + "@fpt.edu.vn";
        String mentorEmail = "mentor-concurrency-" + nonce + "@fpt.edu.vn";

        mockGoogleLogin("token-m1-" + nonce, mentee1Email, "sub-m1-" + nonce, "Mentee 1 " + nonce);
        mockGoogleLogin("token-m2-" + nonce, mentee2Email, "sub-m2-" + nonce, "Mentee 2 " + nonce);
        mockGoogleLogin("token-mentor-" + nonce, mentorEmail, "sub-mentor-" + nonce, "Mentor " + nonce);

        String m1Token = loginAndExtractAccessToken("token-m1-" + nonce);
        String m2Token = loginAndExtractAccessToken("token-m2-" + nonce);
        String mentorToken = loginAndExtractAccessToken("token-mentor-" + nonce);

        User mentee1 = userRepository.findByEmail(mentee1Email).orElseThrow();
        User mentee2 = userRepository.findByEmail(mentee2Email).orElseThrow();
        User mentorUser = userRepository.findByEmail(mentorEmail).orElseThrow();

        AcademicContext academicContext = resolveAcademicContext();
        upsertStudentProfile(m1Token, academicContext, generateStudentCode(31), 5, false);
        upsertStudentProfile(m2Token, academicContext, generateStudentCode(32), 6, false);
        upsertStudentProfile(mentorToken, academicContext, generateStudentCode(33), 8, false);

        mentorUser = userRepository.findByEmail(mentorEmail).orElseThrow();
        userRoleRepository.save(UserRole.builder()
                .id(new UserRoleId(mentorUser.getId(), RoleCode.MENTOR))
                .user(mentorUser)
                .assignedBy(mentorUser)
                .build());

        Tag expertiseTag = createTag("JAVA_CONCURRENCY_" + nonce, "Java Backend", TagType.TECH_SKILL);
        Tag helpTopicTag = createTag("BOOKING_CONCURRENCY_" + nonce, "Booking", TagType.HELP_TOPIC);
        upsertMentorProfile(mentorToken, expertiseTag.getId(), helpTopicTag.getId());
        MentorProfile mentorProfile = mentorProfileRepository.findById(mentorUser.getId()).orElseThrow();
        mentorProfile.setStatus(MentorStatus.ACTIVE);
        mentorProfile.setTeachingMode(TeachingMode.ONLINE);
        mentorProfile.setSessionDuration(60);
        mentorProfile.setVerifiedAt(LocalDateTime.now().minusDays(10));
        mentorProfile.setAvailable(true);
        mentorProfile = mentorProfileRepository.save(mentorProfile);

        MentorAvailabilitySlot slot = createFutureSlot(mentorProfile, 4, 10);

        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch doneLatch = new java.util.concurrent.CountDownLatch(2);

        CreateBookingRequest request = new CreateBookingRequest(
                mentorUser.getId(),
                slot.getId(),
                null,
                "Học lập trình Java",
                "Review CV"
        );

        final java.util.concurrent.atomic.AtomicReference<Exception> exception1 = new java.util.concurrent.atomic.AtomicReference<>();
        final java.util.concurrent.atomic.AtomicReference<Exception> exception2 = new java.util.concurrent.atomic.AtomicReference<>();
        final java.util.concurrent.atomic.AtomicReference<BookingResponse> response1 = new java.util.concurrent.atomic.AtomicReference<>();
        final java.util.concurrent.atomic.AtomicReference<BookingResponse> response2 = new java.util.concurrent.atomic.AtomicReference<>();

        final UUID m1Id = mentee1.getId();
        final UUID m2Id = mentee2.getId();

        executor.submit(() -> {
            try {
                latch.await();
                response1.set(bookingService.createBooking(m1Id, request));
            } catch (Exception e) {
                exception1.set(e);
            } finally {
                doneLatch.countDown();
            }
        });

        executor.submit(() -> {
            try {
                latch.await();
                response2.set(bookingService.createBooking(m2Id, request));
            } catch (Exception e) {
                exception2.set(e);
            } finally {
                doneLatch.countDown();
            }
        });

        latch.countDown();
        doneLatch.await();
        executor.shutdown();

        boolean success1 = response1.get() != null;
        boolean success2 = response2.get() != null;

        assertThat(success1 ^ success2).isTrue();

        if (success1) {
            assertThat(exception2.get()).isNotNull();
            Throwable cause = exception2.get();
            if (cause instanceof BaseException baseEx) {
                assertThat(baseEx.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_CONFLICT);
            } else {
                assertThat(cause).isInstanceOf(org.springframework.dao.ConcurrencyFailureException.class);
            }
        } else {
            assertThat(exception1.get()).isNotNull();
            Throwable cause = exception1.get();
            if (cause instanceof BaseException baseEx) {
                assertThat(baseEx.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_CONFLICT);
            } else {
                assertThat(cause).isInstanceOf(org.springframework.dao.ConcurrencyFailureException.class);
            }
        }
    }
}
