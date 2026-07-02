package com.fptu.exe.skillswap.modules.admin;

import com.fptu.exe.skillswap.infrastructure.security.UserPrincipal;
import com.fptu.exe.skillswap.modules.academic.domain.CampusCode;
import com.fptu.exe.skillswap.modules.academic.domain.StudentProfile;
import com.fptu.exe.skillswap.modules.academic.repository.AcademicProgramRepository;
import com.fptu.exe.skillswap.modules.academic.repository.CampusRepository;
import com.fptu.exe.skillswap.modules.academic.repository.SpecializationRepository;
import com.fptu.exe.skillswap.modules.academic.repository.StudentProfileRepository;
import com.fptu.exe.skillswap.modules.admin.domain.AuditAction;
import com.fptu.exe.skillswap.modules.admin.domain.AuditLog;
import com.fptu.exe.skillswap.modules.admin.repository.AuditLogRepository;
import com.fptu.exe.skillswap.modules.booking.domain.Booking;
import com.fptu.exe.skillswap.modules.booking.domain.BookingStatus;
import com.fptu.exe.skillswap.modules.booking.repository.BookingRepository;
import com.fptu.exe.skillswap.modules.forum.domain.ForumReport;
import com.fptu.exe.skillswap.modules.forum.domain.ForumReportReasonType;
import com.fptu.exe.skillswap.modules.forum.domain.ForumReportTargetType;
import com.fptu.exe.skillswap.modules.forum.repository.ForumReportRepository;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.domain.UserStatus;
import com.fptu.exe.skillswap.modules.identity.repository.UserRepository;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorProfile;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorStatus;
import com.fptu.exe.skillswap.modules.mentor.domain.TeachingMode;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorProfileRepository;
import com.fptu.exe.skillswap.modules.notification.domain.EmailOutbox;
import com.fptu.exe.skillswap.modules.notification.domain.NotificationStatus;
import com.fptu.exe.skillswap.modules.notification.repository.EmailOutboxRepository;
import com.fptu.exe.skillswap.modules.payment.domain.PaymentOrder;
import com.fptu.exe.skillswap.modules.payment.domain.PaymentOrderStatus;
import com.fptu.exe.skillswap.modules.payment.domain.PayoutRequest;
import com.fptu.exe.skillswap.modules.payment.domain.PayoutRequestStatus;
import com.fptu.exe.skillswap.modules.payment.repository.PaymentOrderRepository;
import com.fptu.exe.skillswap.modules.payment.repository.PayoutRequestRepository;
import com.fptu.exe.skillswap.shared.constant.RoleCode;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminDataCenterControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StudentProfileRepository studentProfileRepository;

    @Autowired
    private CampusRepository campusRepository;

    @Autowired
    private AcademicProgramRepository academicProgramRepository;

    @Autowired
    private SpecializationRepository specializationRepository;

    @Autowired
    private MentorProfileRepository mentorProfileRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private PaymentOrderRepository paymentOrderRepository;

    @Autowired
    private PayoutRequestRepository payoutRequestRepository;

    @Autowired
    private ForumReportRepository forumReportRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private EmailOutboxRepository emailOutboxRepository;

    @Autowired
    private EntityManager entityManager;

    private User adminUser;
    private User hiddenAdminUser;
    private User targetUser;
    private User anotherMentee;
    private User anotherMentorUser;
    private MentorProfile targetMentorProfile;
    private MentorProfile anotherMentorProfile;
    private EmailOutbox failedEmailOutbox;

    @BeforeEach
    void setUp() {
        adminUser = saveUser("phase2-admin@test.com", Set.of(RoleCode.ADMIN), UserStatus.ACTIVE);
        hiddenAdminUser = saveUser("phase2-hidden-admin@test.com", Set.of(RoleCode.ADMIN), UserStatus.ACTIVE);
        targetUser = saveUser("phase2-target@test.com", Set.of(RoleCode.MENTEE, RoleCode.MENTOR), UserStatus.ACTIVE);
        anotherMentee = saveUser("phase2-mentee@test.com", Set.of(RoleCode.MENTEE), UserStatus.ACTIVE);
        anotherMentorUser = saveUser("phase2-mentor@test.com", Set.of(RoleCode.MENTOR), UserStatus.ACTIVE);

        var campus = campusRepository.findByCode(CampusCode.HCM).orElseThrow();
        var program = academicProgramRepository.findByCode("CNTT").orElseThrow();
        var specialization = specializationRepository.findByCode("CNTT_TTNT").orElseThrow();

        studentProfileRepository.save(StudentProfile.builder()
                .user(targetUser)
                .claimedStudentCode("HE173001")
                .campus(campus)
                .program(program)
                .specialization(specialization)
                .semester(6)
                .isAlumni(false)
                .build());

        targetMentorProfile = mentorProfileRepository.save(MentorProfile.builder()
                .user(targetUser)
                .status(MentorStatus.ACTIVE)
                .isAvailable(true)
                .verifiedAt(LocalDateTime.of(2026, 7, 1, 9, 0))
                .headline("Backend Mentor")
                .expertiseDescription("Spring Boot")
                .teachingMode(TeachingMode.ONLINE)
                .sessionDuration(60)
                .averageRating(new BigDecimal("4.75"))
                .totalCompletedSessions(11)
                .build());

        anotherMentorProfile = mentorProfileRepository.save(MentorProfile.builder()
                .user(anotherMentorUser)
                .status(MentorStatus.ACTIVE)
                .isAvailable(true)
                .headline("Architecture Mentor")
                .expertiseDescription("System Design")
                .teachingMode(TeachingMode.ONLINE)
                .sessionDuration(60)
                .build());

        seedBookings();
        seedPaymentAndPayout();
        seedForumReport();
        seedAuditLogs();
        seedEmailOutbox();

        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void auditLogs_shouldReturnNewestFirstAndSupportFiltering() throws Exception {
        mockMvc.perform(get("/api/admin/audit-logs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(2)))
                .andExpect(jsonPath("$.data.content[0].action").value("DELETE"))
                .andExpect(jsonPath("$.data.content[0].entityType").value("BOOKING"))
                .andExpect(jsonPath("$.data.content[1].action").value("UPDATE"))
                .andExpect(jsonPath("$.data.content[1].entityType").value("USER"));

        mockMvc.perform(get("/api/admin/audit-logs")
                        .param("actorUserId", adminUser.getId().toString())
                        .param("entityType", "USER")
                        .param("action", "UPDATE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].actorDisplayName").value(adminUser.getFullName()))
                .andExpect(jsonPath("$.data.content[0].oldValue").value("{\"status\":\"ACTIVE\"}"))
                .andExpect(jsonPath("$.data.content[0].newValue").value("{\"status\":\"BANNED\"}"));
    }

    @Test
    void notes_shouldCreateAndListForVisibleUserTarget() throws Exception {
        mockMvc.perform(post("/api/admin/notes")
                        .with(adminAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetType": "USER",
                                  "targetId": "%s",
                                  "note": "Can xem lai lich su moderation"
                                }
                                """.formatted(targetUser.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.targetType").value("USER"))
                .andExpect(jsonPath("$.data.targetId").value(targetUser.getId().toString()))
                .andExpect(jsonPath("$.data.adminUserId").value(adminUser.getId().toString()))
                .andExpect(jsonPath("$.data.adminDisplayName").value(adminUser.getFullName()));

        mockMvc.perform(get("/api/admin/notes")
                        .with(adminAuth())
                        .param("targetType", "USER")
                        .param("targetId", targetUser.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].note").value("Can xem lai lich su moderation"));
    }

    @Test
    void notes_shouldRejectMissingTarget() throws Exception {
        mockMvc.perform(post("/api/admin/notes")
                        .with(adminAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetType": "BOOKING",
                                  "targetId": "%s",
                                  "note": "Khong hop le"
                                }
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void emailOutbox_shouldListWithFiltersAndReturnDetail() throws Exception {
        mockMvc.perform(get("/api/admin/email-outbox")
                        .param("status", "FAILED")
                        .param("templateCode", "BOOKING_ACCEPTED_EMAIL")
                        .param("toEmail", "phase2-target"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].emailOutboxId").value(failedEmailOutbox.getId().toString()))
                .andExpect(jsonPath("$.data.content[0].status").value("FAILED"))
                .andExpect(jsonPath("$.data.content[0].templateCode").value("BOOKING_ACCEPTED_EMAIL"));

        mockMvc.perform(get("/api/admin/email-outbox/{emailOutboxId}", failedEmailOutbox.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.emailOutboxId").value(failedEmailOutbox.getId().toString()))
                .andExpect(jsonPath("$.data.body").value("<html><body>Payment reminder</body></html>"))
                .andExpect(jsonPath("$.data.lastError").value(longEmailError()));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void userSummary_shouldReturnAcademicMentorAndActivityData() throws Exception {
        mockMvc.perform(get("/api/admin/users/{userId}/summary", targetUser.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(targetUser.getId().toString()))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.roles", hasSize(2)))
                .andExpect(jsonPath("$.data.roles[0]").value("MENTEE"))
                .andExpect(jsonPath("$.data.roles[1]").value("MENTOR"))
                .andExpect(jsonPath("$.data.academicProfile.studentCode").value("HE173001"))
                .andExpect(jsonPath("$.data.academicProfile.campusCode").value("HCM"))
                .andExpect(jsonPath("$.data.academicProfile.programCode").value("CNTT"))
                .andExpect(jsonPath("$.data.academicProfile.specializationCode").value("CNTT_TTNT"))
                .andExpect(jsonPath("$.data.academicProfile.semester").value(6))
                .andExpect(jsonPath("$.data.mentorProfile.exists").value(true))
                .andExpect(jsonPath("$.data.mentorProfile.mentorStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.data.mentorProfile.averageRating").value(4.75))
                .andExpect(jsonPath("$.data.mentorProfile.totalCompletedSessions").value(11))
                .andExpect(jsonPath("$.data.activitySummary.menteeBookingCount").value(1))
                .andExpect(jsonPath("$.data.activitySummary.mentorBookingCount").value(1))
                .andExpect(jsonPath("$.data.activitySummary.paymentOrderCount").value(1))
                .andExpect(jsonPath("$.data.activitySummary.payoutRequestCount").value(1))
                .andExpect(jsonPath("$.data.activitySummary.forumReportCreatedCount").value(1));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void userSummary_shouldHideAdminTargetByVisibleUserPolicy() throws Exception {
        mockMvc.perform(get("/api/admin/users/{userId}/summary", hiddenAdminUser.getId()))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "MENTEE")
    void auditLogs_menteeShouldBeForbidden() throws Exception {
        mockMvc.perform(get("/api/admin/audit-logs"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "SYSTEM_ADMIN")
    void emailOutbox_systemAdminShouldBeAllowed() throws Exception {
        mockMvc.perform(get("/api/admin/email-outbox/{emailOutboxId}", failedEmailOutbox.getId()))
                .andExpect(status().isOk());
    }

    private void seedBookings() {
        Booking asMentee = bookingRepository.save(Booking.builder()
                .mentee(targetUser)
                .mentorProfile(anotherMentorProfile)
                .status(BookingStatus.PENDING)
                .learningGoalTitle("Need architecture guidance")
                .selectedStartTime(LocalDateTime.of(2026, 7, 10, 9, 0))
                .selectedEndTime(LocalDateTime.of(2026, 7, 10, 9, 30))
                .build());

        Booking asMentor = bookingRepository.save(Booking.builder()
                .mentee(anotherMentee)
                .mentorProfile(targetMentorProfile)
                .status(BookingStatus.PAID)
                .learningGoalTitle("Need backend guidance")
                .selectedStartTime(LocalDateTime.of(2026, 7, 11, 9, 0))
                .selectedEndTime(LocalDateTime.of(2026, 7, 11, 9, 30))
                .build());

        updateCreatedUpdated("bookings", asMentee.getId(), LocalDateTime.of(2026, 7, 1, 8, 0));
        updateCreatedUpdated("bookings", asMentor.getId(), LocalDateTime.of(2026, 7, 1, 9, 0));
    }

    private void seedPaymentAndPayout() {
        PaymentOrder paymentOrder = paymentOrderRepository.save(PaymentOrder.builder()
                .orderCode("PO-" + UUID.randomUUID())
                .bookingId(UUID.randomUUID())
                .payerUserId(targetUser.getId())
                .mentorUserId(anotherMentorUser.getId())
                .grossScoin(100)
                .remainingPayableScoin(100)
                .mentorNetScoin(80)
                .commissionScoin(20)
                .status(PaymentOrderStatus.PAID)
                .build());
        entityManager.createNativeQuery("""
                update payment_orders
                set created_at = :eventTime,
                    updated_at = :eventTime,
                    paid_at = :eventTime
                where id = :id
                """)
                .setParameter("eventTime", LocalDateTime.of(2026, 7, 1, 10, 0))
                .setParameter("id", paymentOrder.getId())
                .executeUpdate();

        PayoutRequest payoutRequest = payoutRequestRepository.save(PayoutRequest.builder()
                .mentorUserId(targetUser.getId())
                .settlementAccountId(UUID.randomUUID())
                .payoutProfileId(UUID.randomUUID())
                .amountScoin(120)
                .status(PayoutRequestStatus.REQUESTED)
                .bankAccountNameSnapshot("Target User")
                .bankNameSnapshot("SkillSwap Bank")
                .bankAccountNumberMaskedSnapshot("****8888")
                .build());
        entityManager.createNativeQuery("""
                update payout_requests
                set requested_at = :requestedAt,
                    created_at = :requestedAt,
                    updated_at = :requestedAt
                where id = :id
                """)
                .setParameter("requestedAt", LocalDateTime.of(2026, 7, 1, 11, 0))
                .setParameter("id", payoutRequest.getId())
                .executeUpdate();
    }

    private void seedForumReport() {
        ForumReport forumReport = forumReportRepository.save(ForumReport.builder()
                .reporterUser(targetUser)
                .targetType(ForumReportTargetType.POST)
                .targetId(UUID.randomUUID())
                .reasonType(ForumReportReasonType.SPAM)
                .description("Spam content")
                .build());
        updateCreatedUpdated("forum_reports", forumReport.getId(), LocalDateTime.of(2026, 7, 1, 12, 0));
    }

    private void seedAuditLogs() {
        AuditLog userLog = auditLogRepository.save(AuditLog.builder()
                .actor(adminUser)
                .action(AuditAction.UPDATE)
                .entityType("USER")
                .entityId(targetUser.getId())
                .oldValue("{\"status\":\"ACTIVE\"}")
                .newValue("{\"status\":\"BANNED\"}")
                .ipAddress("127.0.0.1")
                .userAgent("JUnit")
                .build());
        AuditLog bookingLog = auditLogRepository.save(AuditLog.builder()
                .actor(adminUser)
                .action(AuditAction.DELETE)
                .entityType("BOOKING")
                .entityId(UUID.randomUUID())
                .oldValue("{\"status\":\"PAID\"}")
                .newValue("{\"status\":\"CANCELLED_BY_ADMIN\"}")
                .ipAddress("127.0.0.2")
                .userAgent("JUnit")
                .build());

        updateAuditLogCreatedAt(userLog.getId(), LocalDateTime.of(2026, 7, 1, 13, 0));
        updateAuditLogCreatedAt(bookingLog.getId(), LocalDateTime.of(2026, 7, 1, 14, 0));
    }

    private void seedEmailOutbox() {
        failedEmailOutbox = emailOutboxRepository.save(EmailOutbox.builder()
                .toEmail(targetUser.getEmail())
                .subject("Payment reminder")
                .body("<html><body>Payment reminder</body></html>")
                .templateCode("BOOKING_ACCEPTED_EMAIL")
                .status(NotificationStatus.FAILED)
                .retryCount(2)
                .lastError(longEmailError())
                .build());
        EmailOutbox sentEmailOutbox = emailOutboxRepository.save(EmailOutbox.builder()
                .toEmail("someone-else@test.com")
                .subject("Mentor approved")
                .body("<html><body>Mentor approved</body></html>")
                .templateCode("MENTOR_APPROVED_EMAIL")
                .status(NotificationStatus.SENT)
                .retryCount(0)
                .sentAt(LocalDateTime.of(2026, 7, 1, 16, 0))
                .build());

        entityManager.createNativeQuery("""
                update email_outbox
                set created_at = :createdAt,
                    sent_at = :sentAt,
                    last_error = :lastError
                where id = :id
                """)
                .setParameter("createdAt", LocalDateTime.of(2026, 7, 1, 15, 0))
                .setParameter("sentAt", null)
                .setParameter("lastError", longEmailError())
                .setParameter("id", failedEmailOutbox.getId())
                .executeUpdate();

        entityManager.createNativeQuery("""
                update email_outbox
                set created_at = :createdAt,
                    sent_at = :sentAt
                where id = :id
                """)
                .setParameter("createdAt", LocalDateTime.of(2026, 7, 1, 16, 0))
                .setParameter("sentAt", LocalDateTime.of(2026, 7, 1, 16, 5))
                .setParameter("id", sentEmailOutbox.getId())
                .executeUpdate();
    }

    private User saveUser(String email, Set<RoleCode> roles, UserStatus status) {
        return userRepository.save(User.builder()
                .email(email)
                .fullName(email)
                .status(status)
                .roles(roles)
                .build());
    }

    private void updateCreatedUpdated(String tableName, UUID id, LocalDateTime timestamp) {
        entityManager.createNativeQuery("""
                update %s
                set created_at = :timestamp,
                    updated_at = :timestamp
                where id = :id
                """.formatted(tableName))
                .setParameter("timestamp", timestamp)
                .setParameter("id", id)
                .executeUpdate();
    }

    private void updateAuditLogCreatedAt(UUID id, LocalDateTime timestamp) {
        entityManager.createNativeQuery("""
                update audit_logs
                set created_at = :timestamp
                where id = :id
                """)
                .setParameter("timestamp", timestamp)
                .setParameter("id", id)
                .executeUpdate();
    }

    private RequestPostProcessor adminAuth() {
        UserPrincipal principal = UserPrincipal.create(adminUser.getId(), adminUser.getEmail(), java.util.List.of(RoleCode.ADMIN));
        return authentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    private String longEmailError() {
        return "Authentication failed while connecting to SMTP relay because the remote server rejected the credentials for this mailbox. Please verify the relay login, password, and authorized IP settings before retrying.";
    }
}
