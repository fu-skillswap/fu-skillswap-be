package com.fptu.exe.skillswap.modules.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fptu.exe.skillswap.modules.booking.domain.Booking;
import com.fptu.exe.skillswap.modules.booking.domain.BookingStatus;
import com.fptu.exe.skillswap.modules.booking.repository.BookingRepository;
import com.fptu.exe.skillswap.modules.forum.domain.ForumReport;
import com.fptu.exe.skillswap.modules.forum.domain.ForumReportReasonType;
import com.fptu.exe.skillswap.modules.forum.domain.ForumReportStatus;
import com.fptu.exe.skillswap.modules.forum.domain.ForumReportTargetType;
import com.fptu.exe.skillswap.modules.forum.repository.ForumReportRepository;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.domain.UserStatus;
import com.fptu.exe.skillswap.modules.identity.repository.UserRepository;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorProfile;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorStatus;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorVerificationRequest;
import com.fptu.exe.skillswap.modules.mentor.domain.TeachingMode;
import com.fptu.exe.skillswap.modules.mentor.domain.VerificationMethod;
import com.fptu.exe.skillswap.modules.mentor.domain.VerificationStatus;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorProfileRepository;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorVerificationRequestRepository;
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
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminDashboardControllerIntegrationTest {

    private static final ZoneId HCM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MentorProfileRepository mentorProfileRepository;

    @Autowired
    private MentorVerificationRequestRepository mentorVerificationRequestRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private ForumReportRepository forumReportRepository;

    @Autowired
    private PayoutRequestRepository payoutRequestRepository;

    @Autowired
    private PaymentOrderRepository paymentOrderRepository;

    @Autowired
    private EmailOutboxRepository emailOutboxRepository;

    @Autowired
    private EntityManager entityManager;

    private LocalDate today;
    private LocalDate fromDate;
    private LocalDate targetUserDate;
    private LocalDate targetVerificationDate;
    private LocalDate targetBookingDate;
    private LocalDate targetPaymentDate;
    private LocalDate targetForumDate;
    private LocalDate targetPayoutDate;
    private LocalDateTime outsideWindowTime;

    @BeforeEach
    void setUp() {
        today = LocalDate.now(HCM_ZONE);
        fromDate = today.minusDays(29);
        targetUserDate = today.minusDays(6);
        targetVerificationDate = today.minusDays(5);
        targetBookingDate = today.minusDays(4);
        targetPaymentDate = today.minusDays(3);
        targetForumDate = today.minusDays(2);
        targetPayoutDate = today.minusDays(1);
        outsideWindowTime = fromDate.minusDays(2).atTime(9, 0);

        User menteeUser = saveUser("dashboard-mentee@test.com", Set.of(RoleCode.MENTEE), UserStatus.ACTIVE);
        User mentorUser = saveUser("dashboard-mentor@test.com", Set.of(RoleCode.MENTOR), UserStatus.ACTIVE);
        User bannedMentee = saveUser("dashboard-banned@test.com", Set.of(RoleCode.MENTEE), UserStatus.BANNED);
        User deletedMentee = saveUser("dashboard-deleted@test.com", Set.of(RoleCode.MENTEE), UserStatus.ACTIVE);
        User adminUser = saveUser("dashboard-admin@test.com", Set.of(RoleCode.ADMIN), UserStatus.ACTIVE);
        User systemAdminUser = saveUser("dashboard-system@test.com", Set.of(RoleCode.SYSTEM_ADMIN), UserStatus.ACTIVE);

        MentorProfile mentorProfile = mentorProfileRepository.save(MentorProfile.builder()
                .user(mentorUser)
                .status(MentorStatus.ACTIVE)
                .headline("Backend Mentor")
                .expertiseDescription("Spring Boot")
                .teachingMode(TeachingMode.ONLINE)
                .sessionDuration(60)
                .isAvailable(true)
                .build());

        seedMentorVerificationRequests(mentorUser);
        seedBookings(menteeUser, mentorProfile);
        seedForumReports(menteeUser);
        seedPayoutRequests(mentorUser.getId());
        seedPaymentOrders(mentorUser.getId(), menteeUser.getId());
        seedEmailOutbox();

        setUserAuditTimestamps(menteeUser.getId(), targetUserDate.atTime(10, 0), null, UserStatus.ACTIVE);
        setUserAuditTimestamps(mentorUser.getId(), outsideWindowTime, null, UserStatus.ACTIVE);
        setUserAuditTimestamps(bannedMentee.getId(), outsideWindowTime, null, UserStatus.BANNED);
        setUserAuditTimestamps(deletedMentee.getId(), outsideWindowTime, outsideWindowTime.plusHours(1), UserStatus.DELETED);
        setUserAuditTimestamps(adminUser.getId(), outsideWindowTime, null, UserStatus.ACTIVE);
        setUserAuditTimestamps(systemAdminUser.getId(), outsideWindowTime, null, UserStatus.ACTIVE);

        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void overview_shouldReturnAggregatedCountsAndRawStatusMaps() throws Exception {
        mockMvc.perform(get("/api/admin/dashboard/overview").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.users.total").value(6))
                .andExpect(jsonPath("$.data.users.active").value(4))
                .andExpect(jsonPath("$.data.users.banned").value(1))
                .andExpect(jsonPath("$.data.users.deleted").value(1))
                .andExpect(jsonPath("$.data.users.menteeOnly").value(2))
                .andExpect(jsonPath("$.data.users.mentor").value(1))
                .andExpect(jsonPath("$.data.mentorVerification.draft").value(1))
                .andExpect(jsonPath("$.data.mentorVerification.pendingReview").value(1))
                .andExpect(jsonPath("$.data.mentorVerification.needsRevision").value(1))
                .andExpect(jsonPath("$.data.mentorVerification.approved").value(1))
                .andExpect(jsonPath("$.data.mentorVerification.rejected").value(1))
                .andExpect(jsonPath("$.data.mentorVerification.withdrawn").value(1))
                .andExpect(jsonPath("$.data.bookings.PENDING").value(1))
                .andExpect(jsonPath("$.data.bookings.ACCEPTED_AWAITING_PAYMENT").value(1))
                .andExpect(jsonPath("$.data.bookings.PAID").value(1))
                .andExpect(jsonPath("$.data.bookings.UNDER_REVIEW").value(1))
                .andExpect(jsonPath("$.data.bookings.CANCELLED_BY_MENTEE").value(1))
                .andExpect(jsonPath("$.data.forumReports.OPEN").value(1))
                .andExpect(jsonPath("$.data.forumReports.DISMISSED").value(1))
                .andExpect(jsonPath("$.data.payoutRequests.REQUESTED").value(1))
                .andExpect(jsonPath("$.data.payoutRequests.PAID").value(1))
                .andExpect(jsonPath("$.data.paymentOrders.FAILED").value(1))
                .andExpect(jsonPath("$.data.paymentOrders.PAID").value(1))
                .andExpect(jsonPath("$.data.emailOutbox.FAILED").value(1))
                .andExpect(jsonPath("$.data.emailOutbox.SENT").value(1));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void queues_shouldReturnDeterministicPriorityOrderAndCounts() throws Exception {
        mockMvc.perform(get("/api/admin/dashboard/queues").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(7)))
                .andExpect(jsonPath("$.data.items[0].key").value("mentor_verification_pending_review"))
                .andExpect(jsonPath("$.data.items[0].count").value(1))
                .andExpect(jsonPath("$.data.items[1].key").value("booking_under_review"))
                .andExpect(jsonPath("$.data.items[1].count").value(1))
                .andExpect(jsonPath("$.data.items[2].key").value("forum_reports_open"))
                .andExpect(jsonPath("$.data.items[2].count").value(1))
                .andExpect(jsonPath("$.data.items[3].key").value("payout_requests_requested"))
                .andExpect(jsonPath("$.data.items[3].count").value(1))
                .andExpect(jsonPath("$.data.items[4].key").value("payment_orders_failed"))
                .andExpect(jsonPath("$.data.items[4].count").value(1))
                .andExpect(jsonPath("$.data.items[5].key").value("email_outbox_failed"))
                .andExpect(jsonPath("$.data.items[5].count").value(1))
                .andExpect(jsonPath("$.data.items[6].key").value("bookings_accepted_awaiting_payment"))
                .andExpect(jsonPath("$.data.items[6].count").value(1));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void timeseries_shouldReturnThirtyPointsAndZeroFilledDays() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/admin/dashboard/timeseries").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.timezone").value("Asia/Ho_Chi_Minh"))
                .andExpect(jsonPath("$.data.points", hasSize(30)))
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode points = root.path("data").path("points");

        assertPoint(points, targetUserDate, "usersCreated", 1);
        assertPoint(points, targetVerificationDate, "mentorVerificationSubmitted", 1);
        assertPoint(points, targetBookingDate, "bookingsCreated", 1);
        assertPoint(points, targetPaymentDate, "paymentOrdersPaid", 1);
        assertPoint(points, targetForumDate, "forumReportsCreated", 1);
        assertPoint(points, targetPayoutDate, "payoutRequestsCreated", 1);
        assertPoint(points, today, "usersCreated", 0);
    }

    @Test
    @WithMockUser(roles = "SYSTEM_ADMIN")
    void overview_systemAdminShouldBeAllowed() throws Exception {
        mockMvc.perform(get("/api/admin/dashboard/overview"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "MENTEE")
    void overview_menteeShouldReceiveForbidden() throws Exception {
        mockMvc.perform(get("/api/admin/dashboard/overview"))
                .andExpect(status().isForbidden());
    }

    private User saveUser(String email, Set<RoleCode> roles, UserStatus status) {
        return userRepository.save(User.builder()
                .email(email)
                .fullName(email)
                .status(status)
                .roles(roles)
                .build());
    }

    private void seedMentorVerificationRequests(User mentorUser) {
        createVerificationRequest(mentorUser, VerificationStatus.DRAFT, outsideWindowTime, null);
        createVerificationRequest(mentorUser, VerificationStatus.PENDING_REVIEW, outsideWindowTime, targetVerificationDate.atTime(10, 0));
        createVerificationRequest(mentorUser, VerificationStatus.NEEDS_REVISION, outsideWindowTime, null);
        createVerificationRequest(mentorUser, VerificationStatus.APPROVED, outsideWindowTime, null);
        createVerificationRequest(mentorUser, VerificationStatus.REJECTED, outsideWindowTime, null);
        createVerificationRequest(mentorUser, VerificationStatus.WITHDRAWN, outsideWindowTime, null);
    }

    private void createVerificationRequest(User mentorUser, VerificationStatus status, LocalDateTime createdAt, LocalDateTime submittedAt) {
        MentorVerificationRequest request = mentorVerificationRequestRepository.save(MentorVerificationRequest.builder()
                .mentor(mentorUser)
                .method(VerificationMethod.MANUAL)
                .status(status)
                .build());
        updateVerificationRequestTimestamps(request.getId(), createdAt, submittedAt);
    }

    private void seedBookings(User menteeUser, MentorProfile mentorProfile) {
        createBooking(menteeUser, mentorProfile, BookingStatus.PENDING, targetBookingDate.atTime(10, 0));
        createBooking(menteeUser, mentorProfile, BookingStatus.ACCEPTED_AWAITING_PAYMENT, outsideWindowTime);
        createBooking(menteeUser, mentorProfile, BookingStatus.PAID, outsideWindowTime);
        createBooking(menteeUser, mentorProfile, BookingStatus.UNDER_REVIEW, outsideWindowTime);
        createBooking(menteeUser, mentorProfile, BookingStatus.CANCELLED_BY_MENTEE, outsideWindowTime);
    }

    private void createBooking(User menteeUser, MentorProfile mentorProfile, BookingStatus status, LocalDateTime createdAt) {
        Booking booking = bookingRepository.save(Booking.builder()
                .mentee(menteeUser)
                .mentorProfile(mentorProfile)
                .status(status)
                .learningGoalTitle("Need help")
                .selectedStartTime(createdAt.plusDays(1))
                .selectedEndTime(createdAt.plusDays(1).plusMinutes(30))
                .build());
        updateTableTimestamps("bookings", booking.getId(), createdAt, createdAt);
    }

    private void seedForumReports(User reporter) {
        createForumReport(reporter, ForumReportStatus.OPEN, targetForumDate.atTime(10, 0));
        createForumReport(reporter, ForumReportStatus.DISMISSED, outsideWindowTime);
    }

    private void createForumReport(User reporter, ForumReportStatus status, LocalDateTime createdAt) {
        ForumReport report = forumReportRepository.save(ForumReport.builder()
                .reporterUser(reporter)
                .targetType(ForumReportTargetType.POST)
                .targetId(UUID.randomUUID())
                .reasonType(ForumReportReasonType.SPAM)
                .description("Report")
                .status(status)
                .build());
        updateTableTimestamps("forum_reports", report.getId(), createdAt, createdAt);
    }

    private void seedPayoutRequests(UUID mentorUserId) {
        createPayoutRequest(mentorUserId, PayoutRequestStatus.REQUESTED, targetPayoutDate.atTime(10, 0));
        createPayoutRequest(mentorUserId, PayoutRequestStatus.PAID, outsideWindowTime);
    }

    private void createPayoutRequest(UUID mentorUserId, PayoutRequestStatus status, LocalDateTime requestedAt) {
        PayoutRequest payoutRequest = payoutRequestRepository.save(PayoutRequest.builder()
                .mentorUserId(mentorUserId)
                .settlementAccountId(UUID.randomUUID())
                .payoutProfileId(UUID.randomUUID())
                .amountScoin(100)
                .status(status)
                .bankAccountNameSnapshot("Mentor")
                .bankNameSnapshot("Test Bank")
                .bankAccountNumberMaskedSnapshot("****1234")
                .build());
        entityManager.createNativeQuery("""
                update payout_requests
                set requested_at = :requestedAt,
                    created_at = :requestedAt,
                    updated_at = :requestedAt
                where id = :id
                """)
                .setParameter("requestedAt", requestedAt)
                .setParameter("id", payoutRequest.getId())
                .executeUpdate();
    }

    private void seedPaymentOrders(UUID mentorUserId, UUID payerUserId) {
        createPaymentOrder(mentorUserId, payerUserId, PaymentOrderStatus.PAID, targetPaymentDate.atTime(10, 0));
        createPaymentOrder(mentorUserId, payerUserId, PaymentOrderStatus.FAILED, outsideWindowTime);
    }

    private void seedEmailOutbox() {
        EmailOutbox failed = emailOutboxRepository.save(EmailOutbox.builder()
                .toEmail("dashboard-failed@test.com")
                .subject("Failed email")
                .body("<html><body>Failed</body></html>")
                .templateCode("BOOKING_ACCEPTED_EMAIL")
                .status(NotificationStatus.FAILED)
                .retryCount(1)
                .lastError("Authentication failed")
                .build());
        EmailOutbox sent = emailOutboxRepository.save(EmailOutbox.builder()
                .toEmail("dashboard-sent@test.com")
                .subject("Sent email")
                .body("<html><body>Sent</body></html>")
                .templateCode("MENTOR_APPROVED_EMAIL")
                .status(NotificationStatus.SENT)
                .retryCount(0)
                .sentAt(outsideWindowTime.plusHours(2))
                .build());

        entityManager.createNativeQuery("""
                update email_outbox
                set created_at = :createdAt,
                    sent_at = :sentAt
                where id = :id
                """)
                .setParameter("createdAt", outsideWindowTime.plusHours(1))
                .setParameter("sentAt", null)
                .setParameter("id", failed.getId())
                .executeUpdate();

        entityManager.createNativeQuery("""
                update email_outbox
                set created_at = :createdAt,
                    sent_at = :sentAt
                where id = :id
                """)
                .setParameter("createdAt", outsideWindowTime.plusHours(2))
                .setParameter("sentAt", outsideWindowTime.plusHours(2))
                .setParameter("id", sent.getId())
                .executeUpdate();
    }

    private void createPaymentOrder(UUID mentorUserId, UUID payerUserId, PaymentOrderStatus status, LocalDateTime eventTime) {
        PaymentOrder paymentOrder = paymentOrderRepository.save(PaymentOrder.builder()
                .orderCode(UUID.randomUUID().toString())
                .bookingId(UUID.randomUUID())
                .payerUserId(payerUserId)
                .mentorUserId(mentorUserId)
                .grossScoin(100)
                .remainingPayableScoin(100)
                .mentorNetScoin(80)
                .commissionScoin(20)
                .status(status)
                .build());

        entityManager.createNativeQuery("""
                update payment_orders
                set created_at = :eventTime,
                    updated_at = :eventTime,
                    paid_at = :paidAt,
                    failed_at = :failedAt
                where id = :id
                """)
                .setParameter("eventTime", eventTime)
                .setParameter("paidAt", status == PaymentOrderStatus.PAID ? eventTime : null)
                .setParameter("failedAt", status == PaymentOrderStatus.FAILED ? eventTime : null)
                .setParameter("id", paymentOrder.getId())
                .executeUpdate();
    }

    private void updateVerificationRequestTimestamps(UUID requestId, LocalDateTime createdAt, LocalDateTime submittedAt) {
        entityManager.createNativeQuery("""
                update mentor_verification_requests
                set created_at = :createdAt,
                    updated_at = :updatedAt,
                    submitted_at = :submittedAt
                where id = :id
                """)
                .setParameter("createdAt", createdAt)
                .setParameter("updatedAt", createdAt)
                .setParameter("submittedAt", submittedAt)
                .setParameter("id", requestId)
                .executeUpdate();
    }

    private void setUserAuditTimestamps(UUID userId, LocalDateTime createdAt, LocalDateTime deletedAt, UserStatus status) {
        entityManager.createNativeQuery("""
                update users
                set created_at = :createdAt,
                    updated_at = :createdAt,
                    deleted_at = :deletedAt,
                    status = :status
                where id = :id
                """)
                .setParameter("createdAt", createdAt)
                .setParameter("deletedAt", deletedAt)
                .setParameter("status", status.name())
                .setParameter("id", userId)
                .executeUpdate();
    }

    private void updateTableTimestamps(String tableName, UUID id, LocalDateTime createdAt, LocalDateTime updatedAt) {
        entityManager.createNativeQuery("""
                update %s
                set created_at = :createdAt,
                    updated_at = :updatedAt
                where id = :id
                """.formatted(tableName))
                .setParameter("createdAt", createdAt)
                .setParameter("updatedAt", updatedAt)
                .setParameter("id", id)
                .executeUpdate();
    }

    private void assertPoint(JsonNode points, LocalDate date, String field, long expectedValue) {
        for (JsonNode point : points) {
            if (date.toString().equals(point.path("date").asText())) {
                org.junit.jupiter.api.Assertions.assertEquals(expectedValue, point.path(field).asLong());
                return;
            }
        }
        org.junit.jupiter.api.Assertions.fail("Missing point for date " + date);
    }
}
