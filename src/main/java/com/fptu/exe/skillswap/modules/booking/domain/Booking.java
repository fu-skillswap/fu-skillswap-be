package com.fptu.exe.skillswap.modules.booking.domain;

import com.fptu.exe.skillswap.modules.identity.domain.User;

import com.fptu.exe.skillswap.shared.persistence.GeneratedUuidV7;
import com.fptu.exe.skillswap.shared.util.DateTimeUtil;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "bookings", indexes = {
    @Index(name = "idx_bookings_mentee_id", columnList = "mentee_user_id"),
    @Index(name = "idx_bookings_mentor_id", columnList = "mentor_user_id"),
    @Index(name = "idx_bookings_status", columnList = "status"),
    @Index(name = "idx_bookings_start_time_utc", columnList = "selected_start_time_utc"),
    @Index(name = "idx_bookings_end_time_utc", columnList = "selected_end_time_utc"),
    @Index(name = "idx_bookings_mentor_status_start_utc", columnList = "mentor_user_id, status, selected_start_time_utc"),
    @Index(name = "idx_bookings_mentee_status_start_utc", columnList = "mentee_user_id, status, selected_start_time_utc"),
    @Index(name = "idx_bookings_status_start_utc", columnList = "status, selected_start_time_utc"),
    @Index(name = "idx_bookings_pending_expire_utc", columnList = "status, pending_expire_at_utc"),
    @Index(name = "idx_bookings_lifecycle_overlap_utc", columnList = "mentor_user_id, status, selected_start_time_utc, selected_end_time_utc")
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

    /** Last line of defence for lifecycle writers that do not hold the booking row lock. */
    @Version
    @Column(nullable = false)
    @Builder.Default
    private Long version = 0L;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "mentee_user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_bookings_mentee"))
    private User mentee;

    @Column(name = "mentor_user_id", nullable = false)
    private UUID mentorUserId;

    @Column(name = "service_id")
    private UUID serviceId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "slot_id", foreignKey = @ForeignKey(name = "fk_bookings_slot"))
    private MentorAvailabilitySlot slot;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Setter(AccessLevel.NONE)
    @Builder.Default
    private BookingStatus status = BookingStatus.PENDING;

    /**
     * Persisted lifecycle status is deliberately writable only inside the booking domain.
     * Application services must use {@link BookingTransitionExecutor} so every transition is
     * validated by {@link BookingStateMachine} and receives its owned timestamps.
     */
    void setStatus(BookingStatus status) {
        this.status = status;
    }

    @Column(name = "learning_goal_title", nullable = false, length = 200)
    private String learningGoalTitle;

    @Column(name = "learning_goal_description", columnDefinition = "TEXT")
    private String learningGoalDescription;

    @Column(name = "selected_start_time_utc")
    private Instant selectedStartTimeUtc;

    /** Legacy business-zone representation retained during the UTC dual-write rollout. */
    @Deprecated
    @Column(name = "selected_start_time")
    private LocalDateTime selectedStartTime;

    @Column(name = "selected_end_time_utc")
    private Instant selectedEndTimeUtc;

    @Deprecated
    @Column(name = "selected_end_time")
    private LocalDateTime selectedEndTime;

    @Deprecated
    @Column(name = "requested_start_time")
    private LocalDateTime requestedStartTime;

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

    @Column(name = "maintain_post_session_chat_snapshot", nullable = false)
    @Builder.Default
    private boolean maintainPostSessionChatSnapshot = false;

    @Column(name = "mentor_response_note", columnDefinition = "TEXT")
    private String mentorResponseNote;

    @Column(name = "reject_reason", columnDefinition = "TEXT")
    private String rejectReason;

    @Column(name = "cancel_reason", columnDefinition = "TEXT")
    private String cancelReason;

    @Column(name = "accepted_at_utc")
    private Instant acceptedAtUtc;

    @Deprecated
    @Column(name = "accepted_at")
    private LocalDateTime acceptedAt;

    @Column(name = "pending_expire_at_utc")
    private Instant pendingExpireAtUtc;

    @Deprecated
    @Column(name = "pending_expire_at")
    private LocalDateTime pendingExpireAt;

    @Column(name = "rejected_at_utc")
    private Instant rejectedAtUtc;

    @Deprecated
    @Column(name = "rejected_at")
    private LocalDateTime rejectedAt;

    @Column(name = "cancelled_at_utc")
    private Instant cancelledAtUtc;

    @Deprecated
    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "completed_at_utc")
    private Instant completedAtUtc;

    @Deprecated
    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "reschedule_count", nullable = false)
    @Builder.Default
    private Integer rescheduleCount = 0;

    @Column(name = "finalized_at_utc")
    private Instant finalizedAtUtc;

    @Deprecated
    @Column(name = "finalized_at")
    private LocalDateTime finalizedAt;

    @Column(name = "auto_closed_at_utc")
    private Instant autoClosedAtUtc;

    @Deprecated
    @Column(name = "auto_closed_at")
    private LocalDateTime autoClosedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "completion_outcome")
    private BookingCompletionOutcome completionOutcome;

    @Column(name = "issue_submitted_at_utc")
    private Instant issueSubmittedAtUtc;

    @Deprecated
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

    @Column(name = "issue_responded_at_utc")
    private Instant issueRespondedAtUtc;

    @Deprecated
    @Column(name = "issue_responded_at")
    private LocalDateTime issueRespondedAt;

    @Column(name = "issue_responded_by_user_id")
    private UUID issueRespondedByUserId;

    @Column(name = "issue_response_note", columnDefinition = "TEXT")
    private String issueResponseNote;

    @Column(name = "mentor_completion_overdue_at_utc")
    private Instant mentorCompletionOverdueAtUtc;

    @Deprecated
    @Column(name = "mentor_completion_overdue_at")
    private LocalDateTime mentorCompletionOverdueAt;

    @Column(name = "post_session_prompted_at_utc")
    private Instant postSessionPromptedAtUtc;

    @Deprecated
    @Column(name = "post_session_prompted_at")
    private LocalDateTime postSessionPromptedAt;

    @Column(name = "mentor_completion_reminder_30m_at_utc")
    private Instant mentorCompletionReminder30mAtUtc;

    @Deprecated
    @Column(name = "mentor_completion_reminder_30m_at")
    private LocalDateTime mentorCompletionReminder30mAt;

    @Column(name = "mentor_completion_reminder_1h_at_utc")
    private Instant mentorCompletionReminder1hAtUtc;

    @Deprecated
    @Column(name = "mentor_completion_reminder_1h_at")
    private LocalDateTime mentorCompletionReminder1hAt;

    @Column(name = "mentee_completion_prompted_at_utc")
    private Instant menteeCompletionPromptedAtUtc;

    @Deprecated
    @Column(name = "mentee_completion_prompted_at")
    private LocalDateTime menteeCompletionPromptedAt;

    @Column(name = "auto_close_warning_sent_at_utc")
    private Instant autoCloseWarningSentAtUtc;

    @Deprecated
    @Column(name = "auto_close_warning_sent_at")
    private LocalDateTime autoCloseWarningSentAt;

    @Column(name = "issue_escalation_sent_at_utc")
    private Instant issueEscalationSentAtUtc;

    @Deprecated
    @Column(name = "issue_escalation_sent_at")
    private LocalDateTime issueEscalationSentAt;

    @Column(name = "issue_human_review_escalated_at_utc")
    private Instant issueHumanReviewEscalatedAtUtc;

    @Column(name = "admin_sla_overdue_at_utc")
    private Instant adminSlaOverdueAtUtc;

    @Column(name = "admin_sla_reminder_count", nullable = false)
    @Builder.Default
    private int adminSlaReminderCount = 0;

    @Column(name = "admin_sla_last_reminder_at_utc")
    private Instant adminSlaLastReminderAtUtc;

    @Column(name = "admin_sla_auto_released_at_utc")
    private Instant adminSlaAutoReleasedAtUtc;

    @Column(name = "admin_sla_warning_sent_at_utc")
    private Instant adminSlaWarningSentAtUtc;

    @Deprecated
    @Column(name = "admin_sla_warning_sent_at")
    private LocalDateTime adminSlaWarningSentAt;

    @Column(name = "issue_resolved_at_utc")
    private Instant issueResolvedAtUtc;

    @Deprecated
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

    @Column(name = "created_at_utc")
    private Instant createdAtUtc;

    @Deprecated
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at_utc")
    private Instant updatedAtUtc;

    @Deprecated
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        syncTimestampPairs();
        Instant nowUtc = DateTimeUtil.instantNow();
        if (createdAtUtc == null) createdAtUtc = nowUtc;
        if (createdAt == null) createdAt = BookingTime.fromInstant(createdAtUtc);
        if (updatedAtUtc == null) updatedAtUtc = nowUtc;
        if (updatedAt == null) updatedAt = BookingTime.fromInstant(updatedAtUtc);
    }

    @PreUpdate
    protected void onUpdate() {
        syncTimestampPairs();
        updatedAtUtc = DateTimeUtil.instantNow();
        updatedAt = BookingTime.fromInstant(updatedAtUtc);
    }

    private void syncTimestampPairs() {
        if (selectedStartTimeUtc == null) selectedStartTimeUtc = BookingTime.toInstant(selectedStartTime); else if (selectedStartTime == null) selectedStartTime = BookingTime.fromInstant(selectedStartTimeUtc);
        if (selectedEndTimeUtc == null) selectedEndTimeUtc = BookingTime.toInstant(selectedEndTime); else if (selectedEndTime == null) selectedEndTime = BookingTime.fromInstant(selectedEndTimeUtc);
        if (acceptedAtUtc == null) acceptedAtUtc = BookingTime.toInstant(acceptedAt); else if (acceptedAt == null) acceptedAt = BookingTime.fromInstant(acceptedAtUtc);
        if (pendingExpireAtUtc == null) pendingExpireAtUtc = BookingTime.toInstant(pendingExpireAt); else if (pendingExpireAt == null) pendingExpireAt = BookingTime.fromInstant(pendingExpireAtUtc);
        if (rejectedAtUtc == null) rejectedAtUtc = BookingTime.toInstant(rejectedAt); else if (rejectedAt == null) rejectedAt = BookingTime.fromInstant(rejectedAtUtc);
        if (cancelledAtUtc == null) cancelledAtUtc = BookingTime.toInstant(cancelledAt); else if (cancelledAt == null) cancelledAt = BookingTime.fromInstant(cancelledAtUtc);
        if (completedAtUtc == null) completedAtUtc = BookingTime.toInstant(completedAt); else if (completedAt == null) completedAt = BookingTime.fromInstant(completedAtUtc);
        if (finalizedAtUtc == null) finalizedAtUtc = BookingTime.toInstant(finalizedAt); else if (finalizedAt == null) finalizedAt = BookingTime.fromInstant(finalizedAtUtc);
        if (autoClosedAtUtc == null) autoClosedAtUtc = BookingTime.toInstant(autoClosedAt); else if (autoClosedAt == null) autoClosedAt = BookingTime.fromInstant(autoClosedAtUtc);
        if (issueSubmittedAtUtc == null) issueSubmittedAtUtc = BookingTime.toInstant(issueSubmittedAt); else if (issueSubmittedAt == null) issueSubmittedAt = BookingTime.fromInstant(issueSubmittedAtUtc);
        if (issueRespondedAtUtc == null) issueRespondedAtUtc = BookingTime.toInstant(issueRespondedAt); else if (issueRespondedAt == null) issueRespondedAt = BookingTime.fromInstant(issueRespondedAtUtc);
        if (issueResolvedAtUtc == null) issueResolvedAtUtc = BookingTime.toInstant(issueResolvedAt); else if (issueResolvedAt == null) issueResolvedAt = BookingTime.fromInstant(issueResolvedAtUtc);
        if (mentorCompletionOverdueAtUtc == null) mentorCompletionOverdueAtUtc = BookingTime.toInstant(mentorCompletionOverdueAt); else if (mentorCompletionOverdueAt == null) mentorCompletionOverdueAt = BookingTime.fromInstant(mentorCompletionOverdueAtUtc);
        if (postSessionPromptedAtUtc == null) postSessionPromptedAtUtc = BookingTime.toInstant(postSessionPromptedAt); else if (postSessionPromptedAt == null) postSessionPromptedAt = BookingTime.fromInstant(postSessionPromptedAtUtc);
        if (mentorCompletionReminder30mAtUtc == null) mentorCompletionReminder30mAtUtc = BookingTime.toInstant(mentorCompletionReminder30mAt); else if (mentorCompletionReminder30mAt == null) mentorCompletionReminder30mAt = BookingTime.fromInstant(mentorCompletionReminder30mAtUtc);
        if (mentorCompletionReminder1hAtUtc == null) mentorCompletionReminder1hAtUtc = BookingTime.toInstant(mentorCompletionReminder1hAt); else if (mentorCompletionReminder1hAt == null) mentorCompletionReminder1hAt = BookingTime.fromInstant(mentorCompletionReminder1hAtUtc);
        if (menteeCompletionPromptedAtUtc == null) menteeCompletionPromptedAtUtc = BookingTime.toInstant(menteeCompletionPromptedAt); else if (menteeCompletionPromptedAt == null) menteeCompletionPromptedAt = BookingTime.fromInstant(menteeCompletionPromptedAtUtc);
        if (autoCloseWarningSentAtUtc == null) autoCloseWarningSentAtUtc = BookingTime.toInstant(autoCloseWarningSentAt); else if (autoCloseWarningSentAt == null) autoCloseWarningSentAt = BookingTime.fromInstant(autoCloseWarningSentAtUtc);
        if (issueEscalationSentAtUtc == null) issueEscalationSentAtUtc = BookingTime.toInstant(issueEscalationSentAt); else if (issueEscalationSentAt == null) issueEscalationSentAt = BookingTime.fromInstant(issueEscalationSentAtUtc);
        if (adminSlaWarningSentAtUtc == null) adminSlaWarningSentAtUtc = BookingTime.toInstant(adminSlaWarningSentAt); else if (adminSlaWarningSentAt == null) adminSlaWarningSentAt = BookingTime.fromInstant(adminSlaWarningSentAtUtc);
    }
}
