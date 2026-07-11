package com.fptu.exe.skillswap.modules.admin;

import com.fptu.exe.skillswap.infrastructure.security.UserPrincipal;
import com.fptu.exe.skillswap.modules.admin.domain.AdminNote;
import com.fptu.exe.skillswap.modules.admin.repository.AdminNoteRepository;
import com.fptu.exe.skillswap.modules.booking.domain.Booking;
import com.fptu.exe.skillswap.modules.booking.domain.BookingStatus;
import com.fptu.exe.skillswap.modules.booking.repository.BookingRepository;
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
import com.fptu.exe.skillswap.shared.constant.RoleCode;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
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
class AdminOperationsWorkbenchIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MentorProfileRepository mentorProfileRepository;

    @Autowired
    private MentorVerificationRequestRepository mentorVerificationRequestRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private EmailOutboxRepository emailOutboxRepository;

    @Autowired
    private AdminNoteRepository adminNoteRepository;

    @Autowired
    private EntityManager entityManager;

    private User adminUser;
    private User secondAdminUser;
    private User systemAdminUser;
    private User menteeUser;
    private User mentorUser;
    private Booking underReviewBooking;
    private EmailOutbox failedEmailOutbox;
    private EmailOutbox sentEmailOutbox;
    private MentorVerificationRequest lockedVerificationRequest;
    private MentorVerificationRequest secondLockedVerificationRequest;

    @BeforeEach
    void setUp() {
        adminUser = saveUser("phase3-admin@test.com", Set.of(RoleCode.ADMIN), UserStatus.ACTIVE);
        secondAdminUser = saveUser("phase3-admin-2@test.com", Set.of(RoleCode.ADMIN), UserStatus.ACTIVE);
        systemAdminUser = saveUser("phase3-system@test.com", Set.of(RoleCode.SYSTEM_ADMIN), UserStatus.ACTIVE);
        menteeUser = saveUser("phase3-mentee@test.com", Set.of(RoleCode.MENTEE), UserStatus.ACTIVE);
        mentorUser = saveUser("phase3-mentor@test.com", Set.of(RoleCode.MENTOR), UserStatus.ACTIVE);

        MentorProfile mentorProfile = mentorProfileRepository.save(MentorProfile.builder()
                .user(mentorUser)
                .status(MentorStatus.ACTIVE)
                .headline("Architecture Mentor")
                .expertiseDescription("System Design")
                .teachingMode(TeachingMode.ONLINE)
                .sessionDuration(60)
                .isAvailable(true)
                .build());

        underReviewBooking = bookingRepository.save(Booking.builder()
                .mentee(menteeUser)
                .mentorProfile(mentorProfile)
                .status(BookingStatus.UNDER_REVIEW)
                .learningGoalTitle("Need architecture review")
                .selectedStartTime(LocalDateTime.of(2026, 7, 10, 9, 0))
                .selectedEndTime(LocalDateTime.of(2026, 7, 10, 9, 30))
                .build());
        updateCreatedUpdated("bookings", underReviewBooking.getId(), LocalDateTime.of(2026, 7, 1, 8, 0));

        failedEmailOutbox = emailOutboxRepository.save(EmailOutbox.builder()
                .toEmail(menteeUser.getEmail())
                .subject("Payment reminder")
                .body("<html><body>Payment reminder</body></html>")
                .templateCode("BOOKING_ACCEPTED_EMAIL")
                .status(NotificationStatus.FAILED)
                .retryCount(2)
                .lastError("SMTP authentication failed")
                .build());
        sentEmailOutbox = emailOutboxRepository.save(EmailOutbox.builder()
                .toEmail("sent@test.com")
                .subject("Sent email")
                .body("<html><body>Sent</body></html>")
                .templateCode("MENTOR_APPROVED_EMAIL")
                .status(NotificationStatus.SENT)
                .retryCount(0)
                .sentAt(LocalDateTime.of(2026, 7, 1, 10, 10))
                .build());
        updateEmailOutbox(failedEmailOutbox.getId(), LocalDateTime.of(2026, 7, 1, 9, 0), null);
        updateEmailOutbox(sentEmailOutbox.getId(), LocalDateTime.of(2026, 7, 1, 10, 0), LocalDateTime.of(2026, 7, 1, 10, 10));

        lockedVerificationRequest = mentorVerificationRequestRepository.save(MentorVerificationRequest.builder()
                .mentor(mentorUser)
                .method(VerificationMethod.MANUAL)
                .status(VerificationStatus.PENDING_REVIEW)
                .submittedAt(LocalDateTime.of(2026, 7, 1, 9, 0))
                .lockedBy(adminUser)
                .lockedAt(LocalDateTime.of(2026, 7, 1, 9, 5))
                .lockExpiresAt(LocalDateTime.of(2026, 7, 1, 9, 10))
                .build());
        secondLockedVerificationRequest = mentorVerificationRequestRepository.save(MentorVerificationRequest.builder()
                .mentor(mentorUser)
                .method(VerificationMethod.MANUAL)
                .status(VerificationStatus.PENDING_REVIEW)
                .submittedAt(LocalDateTime.of(2026, 7, 1, 9, 30))
                .lockedBy(adminUser)
                .lockedAt(LocalDateTime.of(2026, 7, 1, 9, 35))
                .lockExpiresAt(LocalDateTime.of(2026, 7, 1, 9, 40))
                .build());

        adminNoteRepository.save(AdminNote.builder()
                .targetType("BOOKING")
                .targetId(underReviewBooking.getId())
                .adminUser(adminUser)
                .note("Can theo doi de xu ly tiep")
                .build());

        entityManager.flush();
        entityManager.clear();
    }

    @Test
    void queueItems_shouldSupportUnassignedAndAssignedToMeFilters() throws Exception {
        mockMvc.perform(get("/api/admin/dashboard/queue-items")
                        .with(adminAuth())
                        .param("queueKey", "booking_under_review")
                        .param("unassignedOnly", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].caseType").value("BOOKING"))
                .andExpect(jsonPath("$.data.content[0].detailPath").value("/api/admin/bookings/" + underReviewBooking.getId()))
                .andExpect(jsonPath("$.data.content[0].availableActions[0]").value("VIEW_DETAIL"));

        mockMvc.perform(post("/api/admin/cases/BOOKING/{caseId}/assign", underReviewBooking.getId())
                        .with(adminAuth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.assigned").value(true))
                .andExpect(jsonPath("$.data.assignedAdminUserId").value(adminUser.getId().toString()));

        mockMvc.perform(get("/api/admin/dashboard/queue-items")
                        .with(adminAuth())
                        .param("queueKey", "booking_under_review")
                        .param("assignedToMe", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].assignedAdminUserId").value(adminUser.getId().toString()));
    }

    @Test
    void ownership_shouldBeIdempotentAndProtectForeignAssignments() throws Exception {
        mockMvc.perform(post("/api/admin/cases/BOOKING/{caseId}/assign", underReviewBooking.getId())
                        .with(adminAuth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.assigned").value(true));

        mockMvc.perform(post("/api/admin/cases/BOOKING/{caseId}/assign", underReviewBooking.getId())
                        .with(adminAuth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.assignedAdminUserId").value(adminUser.getId().toString()));

        mockMvc.perform(post("/api/admin/cases/BOOKING/{caseId}/assign", underReviewBooking.getId())
                        .with(secondAdminAuth()))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/admin/cases/BOOKING/{caseId}/unassign", underReviewBooking.getId())
                        .with(secondAdminAuth()))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/admin/cases/BOOKING/{caseId}/unassign", underReviewBooking.getId())
                        .with(systemAdminAuth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.assigned").value(false));
    }

    @Test
    void activity_shouldMergeAdminNotesAndOperatorAuditLogs() throws Exception {
        mockMvc.perform(post("/api/admin/cases/BOOKING/{caseId}/assign", underReviewBooking.getId())
                        .with(adminAuth()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/cases/BOOKING/{caseId}/activity", underReviewBooking.getId())
                        .with(adminAuth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(2)))
                .andExpect(jsonPath("$.data.content[0].eventType").value("CASE_ASSIGNMENT"))
                .andExpect(jsonPath("$.data.content[1].eventType").value("ADMIN_NOTE"));
    }

    @Test
    void emailOutboxRetry_shouldResetFailedRowAndRejectNonFailedRows() throws Exception {
        mockMvc.perform(post("/api/admin/email-outbox/{emailOutboxId}/retry", failedEmailOutbox.getId())
                        .with(adminAuth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.retryCount").value(3))
                .andExpect(jsonPath("$.data.lastError").doesNotExist());

        mockMvc.perform(post("/api/admin/email-outbox/{emailOutboxId}/retry", sentEmailOutbox.getId())
                        .with(adminAuth()))
                .andExpect(status().isConflict());
    }

    @Test
    void notes_shouldSupportEmailOutboxTarget() throws Exception {
        mockMvc.perform(post("/api/admin/notes")
                        .with(adminAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetType": "EMAIL_OUTBOX",
                                  "targetId": "%s",
                                  "note": "Can kiem tra SMTP relay"
                                }
                                """.formatted(failedEmailOutbox.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.targetType").value("EMAIL_OUTBOX"))
                .andExpect(jsonPath("$.data.targetId").value(failedEmailOutbox.getId().toString()));
    }

    @Test
    void releaseVerificationLock_shouldAllowOwnerAndSystemAdmin() throws Exception {
        mockMvc.perform(post("/api/admin/mentor-verification/requests/{requestId}/lock/release", lockedVerificationRequest.getId())
                        .with(adminAuth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.locked").value(false));

        mockMvc.perform(post("/api/admin/mentor-verification/requests/{requestId}/lock/release", secondLockedVerificationRequest.getId())
                        .with(secondAdminAuth()))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/admin/mentor-verification/requests/{requestId}/lock/release", secondLockedVerificationRequest.getId())
                        .with(systemAdminAuth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.locked").value(false));
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

    private void updateEmailOutbox(UUID id, LocalDateTime createdAt, LocalDateTime sentAt) {
        entityManager.createNativeQuery("""
                update email_outbox
                set created_at = :createdAt,
                    sent_at = :sentAt
                where id = :id
                """)
                .setParameter("createdAt", createdAt)
                .setParameter("sentAt", sentAt)
                .setParameter("id", id)
                .executeUpdate();
    }

    private RequestPostProcessor adminAuth() {
        return authFor(adminUser.getId(), adminUser.getEmail(), List.of(RoleCode.ADMIN));
    }

    private RequestPostProcessor secondAdminAuth() {
        return authFor(secondAdminUser.getId(), secondAdminUser.getEmail(), List.of(RoleCode.ADMIN));
    }

    private RequestPostProcessor systemAdminAuth() {
        return authFor(systemAdminUser.getId(), systemAdminUser.getEmail(), List.of(RoleCode.SYSTEM_ADMIN));
    }

    private RequestPostProcessor authFor(UUID userId, String email, List<RoleCode> roles) {
        UserPrincipal principal = UserPrincipal.create(userId, email, roles);
        return authentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }
}
