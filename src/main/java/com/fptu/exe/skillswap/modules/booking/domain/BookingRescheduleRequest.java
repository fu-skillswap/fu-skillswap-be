package com.fptu.exe.skillswap.modules.booking.domain;

import com.fptu.exe.skillswap.modules.booking.service.BookingTime;
import com.fptu.exe.skillswap.shared.persistence.GeneratedUuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "booking_reschedule_requests", indexes = {
        @Index(name = "idx_booking_reschedule_booking_id", columnList = "booking_id"),
        @Index(name = "idx_booking_reschedule_status", columnList = "status"),
        @Index(name = "idx_booking_reschedule_requester", columnList = "requester_role"),
        @Index(name = "idx_booking_reschedule_requested_by", columnList = "requested_by_user_id"),
        @Index(name = "idx_booking_reschedule_responded_by", columnList = "responded_by_user_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingRescheduleRequest {

    @Id
    @GeneratedUuidV7
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "booking_id", nullable = false, foreignKey = @ForeignKey(name = "fk_booking_reschedule_booking"))
    private Booking booking;

    @ManyToOne(optional = false)
    @JoinColumn(name = "current_slot_id", nullable = false, foreignKey = @ForeignKey(name = "fk_booking_reschedule_current_slot"))
    private MentorAvailabilitySlot currentSlot;

    @ManyToOne(optional = false)
    @JoinColumn(name = "proposed_slot_id", nullable = false, foreignKey = @ForeignKey(name = "fk_booking_reschedule_proposed_slot"))
    private MentorAvailabilitySlot proposedSlot;

    @Column(name = "requested_by_user_id", nullable = false)
    private UUID requestedByUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "requester_role", nullable = false, length = 20)
    private BookingRescheduleActorRole requesterRole;

    @Column(name = "responded_by_user_id")
    private UUID respondedByUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "responder_role", length = 20)
    private BookingRescheduleActorRole responderRole;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private BookingRescheduleStatus status;

    @Column(name = "request_reason", nullable = false, columnDefinition = "TEXT")
    private String requestReason;

    @Column(name = "response_note", columnDefinition = "TEXT")
    private String responseNote;

    @Column(name = "previous_selected_start_time", nullable = false)
    private LocalDateTime previousSelectedStartTime;

    @Column(name = "previous_selected_start_time_utc")
    private Instant previousSelectedStartTimeUtc;

    @Column(name = "previous_selected_end_time", nullable = false)
    private LocalDateTime previousSelectedEndTime;

    @Column(name = "previous_selected_end_time_utc")
    private Instant previousSelectedEndTimeUtc;

    @Column(name = "proposed_selected_start_time", nullable = false)
    private LocalDateTime proposedSelectedStartTime;

    @Column(name = "proposed_selected_start_time_utc")
    private Instant proposedSelectedStartTimeUtc;

    @Column(name = "proposed_selected_end_time", nullable = false)
    private LocalDateTime proposedSelectedEndTime;

    @Column(name = "proposed_selected_end_time_utc")
    private Instant proposedSelectedEndTimeUtc;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    @Column(name = "requested_at_utc")
    private Instant requestedAtUtc;

    @Column(name = "responded_at")
    private LocalDateTime respondedAt;

    @Column(name = "responded_at_utc")
    private Instant respondedAtUtc;

    @Column(name = "expired_at")
    private LocalDateTime expiredAt;

    @Column(name = "expired_at_utc")
    private Instant expiredAtUtc;

    @Column(name = "admin_override", nullable = false)
    @Builder.Default
    private boolean adminOverride = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "created_at_utc")
    private Instant createdAtUtc;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "updated_at_utc")
    private Instant updatedAtUtc;

    @PrePersist
    public void onCreate() {
        syncShadowFields();
        if (createdAtUtc == null && createdAt == null) {
            createdAtUtc = com.fptu.exe.skillswap.shared.util.DateTimeUtil.instantNow();
            createdAt = BookingTime.fromInstant(createdAtUtc);
        } else if (createdAtUtc == null) {
            createdAtUtc = BookingTime.toInstant(createdAt);
        } else if (createdAt == null) {
            createdAt = BookingTime.fromInstant(createdAtUtc);
        }
        if (updatedAtUtc == null && updatedAt == null) {
            updatedAtUtc = createdAtUtc;
            updatedAt = createdAt;
        } else if (updatedAtUtc == null) {
            updatedAtUtc = BookingTime.toInstant(updatedAt);
        } else if (updatedAt == null) {
            updatedAt = BookingTime.fromInstant(updatedAtUtc);
        }
    }

    @PreUpdate
    public void onUpdate() {
        syncShadowFields();
        updatedAtUtc = com.fptu.exe.skillswap.shared.util.DateTimeUtil.instantNow();
        updatedAt = BookingTime.fromInstant(updatedAtUtc);
    }

    private void syncShadowFields() {
        if (previousSelectedStartTimeUtc != null && previousSelectedStartTime == null) {
            previousSelectedStartTime = BookingTime.fromInstant(previousSelectedStartTimeUtc);
        } else if (previousSelectedStartTime != null && previousSelectedStartTimeUtc == null) {
            previousSelectedStartTimeUtc = BookingTime.toInstant(previousSelectedStartTime);
        }

        if (previousSelectedEndTimeUtc != null && previousSelectedEndTime == null) {
            previousSelectedEndTime = BookingTime.fromInstant(previousSelectedEndTimeUtc);
        } else if (previousSelectedEndTime != null && previousSelectedEndTimeUtc == null) {
            previousSelectedEndTimeUtc = BookingTime.toInstant(previousSelectedEndTime);
        }

        if (proposedSelectedStartTimeUtc != null && proposedSelectedStartTime == null) {
            proposedSelectedStartTime = BookingTime.fromInstant(proposedSelectedStartTimeUtc);
        } else if (proposedSelectedStartTime != null && proposedSelectedStartTimeUtc == null) {
            proposedSelectedStartTimeUtc = BookingTime.toInstant(proposedSelectedStartTime);
        }

        if (proposedSelectedEndTimeUtc != null && proposedSelectedEndTime == null) {
            proposedSelectedEndTime = BookingTime.fromInstant(proposedSelectedEndTimeUtc);
        } else if (proposedSelectedEndTime != null && proposedSelectedEndTimeUtc == null) {
            proposedSelectedEndTimeUtc = BookingTime.toInstant(proposedSelectedEndTime);
        }

        if (requestedAtUtc != null && requestedAt == null) {
            requestedAt = BookingTime.fromInstant(requestedAtUtc);
        } else if (requestedAt != null && requestedAtUtc == null) {
            requestedAtUtc = BookingTime.toInstant(requestedAt);
        }

        if (respondedAtUtc != null && respondedAt == null) {
            respondedAt = BookingTime.fromInstant(respondedAtUtc);
        } else if (respondedAt != null && respondedAtUtc == null) {
            respondedAtUtc = BookingTime.toInstant(respondedAt);
        }

        if (expiredAtUtc != null && expiredAt == null) {
            expiredAt = BookingTime.fromInstant(expiredAtUtc);
        } else if (expiredAt != null && expiredAtUtc == null) {
            expiredAtUtc = BookingTime.toInstant(expiredAt);
        }
    }
}
