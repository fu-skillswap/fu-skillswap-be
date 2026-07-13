package com.fptu.exe.skillswap.modules.booking.domain;

import com.fptu.exe.skillswap.shared.util.DateTimeUtil;

import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorProfile;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorService;
import com.fptu.exe.skillswap.shared.persistence.GeneratedUuidV7;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "bookings", indexes = {
    @Index(name = "idx_bookings_mentee_id", columnList = "mentee_user_id"),
    @Index(name = "idx_bookings_mentor_id", columnList = "mentor_user_id"),
    @Index(name = "idx_bookings_status", columnList = "status"),
    @Index(name = "idx_bookings_start_time", columnList = "selected_start_time"),
    @Index(name = "idx_bookings_mentee_status_time", columnList = "mentee_user_id, status, selected_start_time"),
    @Index(name = "idx_bookings_mentor_status_time", columnList = "mentor_user_id, status, selected_start_time"),
    @Index(name = "idx_bookings_status_selected_start_time", columnList = "status, selected_start_time"),
    @Index(name = "idx_bookings_mentee_status_selected_time", columnList = "mentee_user_id, status, selected_start_time, selected_end_time")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Booking {

    @Id
    @GeneratedUuidV7
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "mentee_user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_bookings_mentee"))
    private User mentee;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "mentor_user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_bookings_mentor"))
    private MentorProfile mentorProfile;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "service_id", nullable = true, foreignKey = @ForeignKey(name = "fk_bookings_service"))
    private MentorService service;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "slot_id", foreignKey = @ForeignKey(name = "fk_bookings_slot"))
    private MentorAvailabilitySlot slot;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private BookingStatus status = BookingStatus.PENDING;

    @Column(name = "learning_goal_title", nullable = false, length = 200)
    private String learningGoalTitle;

    @Column(name = "learning_goal_description", columnDefinition = "TEXT")
    private String learningGoalDescription;

    @Column(name = "selected_start_time")
    private LocalDateTime selectedStartTime;

    @Column(name = "selected_end_time")
    private LocalDateTime selectedEndTime;

    // Deferred-drop legacy columns kept only for DB compatibility during migration rollout.
    @Deprecated
    @Column(name = "requested_start_time")
    private LocalDateTime requestedStartTime;

    // Deferred-drop legacy columns kept only for DB compatibility during migration rollout.
    @Deprecated
    @Column(name = "requested_end_time")
    private LocalDateTime requestedEndTime;

    @Column(name = "service_title_snapshot", length = 200)
    private String serviceTitleSnapshot;

    @Column(name = "service_description_snapshot", columnDefinition = "TEXT")
    private String serviceDescriptionSnapshot;

    @Column(name = "service_duration_snapshot")
    private Integer serviceDurationSnapshot;

    @Column(name = "service_expected_outcome_snapshot", columnDefinition = "TEXT")
    private String serviceExpectedOutcomeSnapshot;

    @Column(name = "service_is_free_snapshot")
    private Boolean serviceIsFreeSnapshot;

    @Column(name = "service_price_scoin_snapshot")
    private Integer servicePriceScoinSnapshot;

    @Column(name = "mentor_response_note", columnDefinition = "TEXT")
    private String mentorResponseNote;

    @Column(name = "reject_reason", columnDefinition = "TEXT")
    private String rejectReason;

    @Column(name = "cancel_reason", columnDefinition = "TEXT")
    private String cancelReason;

    @Column(name = "accepted_at")
    private LocalDateTime acceptedAt;

    @Column(name = "rejected_at")
    private LocalDateTime rejectedAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "reschedule_count", nullable = false)
    @Builder.Default
    private Integer rescheduleCount = 0;

    @Column(name = "finalized_at")
    private LocalDateTime finalizedAt;

    @Column(name = "auto_closed_at")
    private LocalDateTime autoClosedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "completion_outcome")
    private BookingCompletionOutcome completionOutcome;

    @Column(name = "issue_submitted_at")
    private LocalDateTime issueSubmittedAt;

    @Column(name = "issue_submitted_by_user_id")
    private UUID issueSubmittedByUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "issue_type")
    private BookingIssueType issueType;

    @Column(name = "issue_description", columnDefinition = "TEXT")
    private String issueDescription;

    /** Deprecated DB column retained only for migration compatibility. */
    @Deprecated
    @Column(name = "wants_admin_review")
    private Boolean wantsAdminReview;

    @Column(name = "issue_responded_at")
    private LocalDateTime issueRespondedAt;

    @Column(name = "issue_responded_by_user_id")
    private UUID issueRespondedByUserId;

    @Column(name = "issue_response_note", columnDefinition = "TEXT")
    private String issueResponseNote;

    @Column(name = "mentor_completion_overdue_at")
    private LocalDateTime mentorCompletionOverdueAt;

    @Column(name = "post_session_prompted_at")
    private LocalDateTime postSessionPromptedAt;

    @Column(name = "mentor_completion_reminder_30m_at")
    private LocalDateTime mentorCompletionReminder30mAt;

    @Column(name = "mentor_completion_reminder_1h_at")
    private LocalDateTime mentorCompletionReminder1hAt;

    @Column(name = "mentee_completion_prompted_at")
    private LocalDateTime menteeCompletionPromptedAt;

    @Column(name = "auto_close_warning_sent_at")
    private LocalDateTime autoCloseWarningSentAt;

    @Column(name = "issue_escalation_sent_at")
    private LocalDateTime issueEscalationSentAt;

    @Column(name = "issue_resolved_at")
    private LocalDateTime issueResolvedAt;

    @Column(name = "issue_resolved_by_user_id")
    private UUID issueResolvedByUserId;

    @Column(name = "issue_resolution_note", columnDefinition = "TEXT")
    private String issueResolutionNote;

    @Deprecated
    @Enumerated(EnumType.STRING)
    @Column(name = "meeting_platform")
    private MeetingPlatform meetingPlatform;

    @Deprecated
    @Column(name = "meeting_link", columnDefinition = "TEXT")
    private String meetingLink;

    @Deprecated
    @Column(columnDefinition = "TEXT")
    private String location;

    @Deprecated
    @Column(name = "actual_start_time")
    private LocalDateTime actualStartTime;

    @Deprecated
    @Column(name = "actual_end_time")
    private LocalDateTime actualEndTime;

    @Column(name = "mentor_note", columnDefinition = "TEXT")
    private String mentorNote;

    @Column(name = "mentee_note", columnDefinition = "TEXT")
    private String menteeNote;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = DateTimeUtil.now();
        updatedAt = DateTimeUtil.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = DateTimeUtil.now();
    }
}




