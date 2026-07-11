package com.fptu.exe.skillswap.modules.booking.domain;

import com.fptu.exe.skillswap.shared.persistence.GeneratedUuidV7;
import com.fptu.exe.skillswap.shared.util.DateTimeUtil;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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

    @Column(name = "previous_selected_end_time", nullable = false)
    private LocalDateTime previousSelectedEndTime;

    @Column(name = "proposed_selected_start_time", nullable = false)
    private LocalDateTime proposedSelectedStartTime;

    @Column(name = "proposed_selected_end_time", nullable = false)
    private LocalDateTime proposedSelectedEndTime;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    @Column(name = "responded_at")
    private LocalDateTime respondedAt;

    @Column(name = "expired_at")
    private LocalDateTime expiredAt;

    @Column(name = "admin_override", nullable = false)
    @Builder.Default
    private boolean adminOverride = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @jakarta.persistence.PrePersist
    protected void onCreate() {
        createdAt = DateTimeUtil.now();
        updatedAt = DateTimeUtil.now();
    }

    @jakarta.persistence.PreUpdate
    protected void onUpdate() {
        updatedAt = DateTimeUtil.now();
    }
}
