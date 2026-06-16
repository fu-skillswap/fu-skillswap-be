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
    @Index(name = "idx_bookings_start_time", columnList = "requested_start_time"),
    @Index(name = "idx_bookings_mentee_status_time", columnList = "mentee_user_id, status, requested_start_time"),
    @Index(name = "idx_bookings_mentor_status_time", columnList = "mentor_user_id, status, requested_start_time")
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id", foreignKey = @ForeignKey(name = "fk_bookings_service"))
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

    @Column(name = "requested_start_time", nullable = false)
    private LocalDateTime requestedStartTime;

    @Column(name = "requested_end_time", nullable = false)
    private LocalDateTime requestedEndTime;

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

    @Enumerated(EnumType.STRING)
    @Column(name = "meeting_platform")
    private MeetingPlatform meetingPlatform;

    @Column(name = "meeting_link", columnDefinition = "TEXT")
    private String meetingLink;

    @Column(columnDefinition = "TEXT")
    private String location;

    @Column(name = "actual_start_time")
    private LocalDateTime actualStartTime;

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




