package com.fptu.exe.skillswap.modules.booking.domain;

import com.fptu.exe.skillswap.modules.mentor.domain.MentorProfile;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorService;
import com.fptu.exe.skillswap.shared.persistence.GeneratedUuidV7;
import com.fptu.exe.skillswap.shared.util.DateTimeUtil;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/** A scheduled group event. Individual attendee commerce is added in Phase 2. */
@Entity
@Table(name = "group_sessions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupSession {

    @Id
    @GeneratedUuidV7
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_id", nullable = false, foreignKey = @ForeignKey(name = "fk_group_sessions_service"))
    private MentorService service;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "mentor_user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_group_sessions_mentor"))
    private MentorProfile mentorProfile;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_slot_id", nullable = false, foreignKey = @ForeignKey(name = "fk_group_sessions_source_slot"))
    private MentorAvailabilitySlot sourceSlot;

    @Column(name = "scheduled_start_at", nullable = false)
    private LocalDateTime scheduledStartAt;

    @Column(name = "scheduled_end_at", nullable = false)
    private LocalDateTime scheduledEndAt;

    @Column(name = "max_participants", nullable = false)
    private int maxParticipants;

    @Column(name = "reserved_seat_count", nullable = false)
    @Builder.Default
    private int reservedSeatCount = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    @Builder.Default
    private GroupSessionStatus status = GroupSessionStatus.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(name = "registration_status", nullable = false, length = 32)
    @Builder.Default
    private GroupSessionRegistrationStatus registrationStatus = GroupSessionRegistrationStatus.OPEN;

    @Column(name = "registration_closes_at", nullable = false)
    private LocalDateTime registrationClosesAt;

    @Column(name = "session_note", length = 1000)
    private String sessionNote;

    @Column(name = "service_title_snapshot", length = 200)
    private String serviceTitleSnapshot;

    @Column(name = "service_description_snapshot", columnDefinition = "TEXT")
    private String serviceDescriptionSnapshot;

    @Column(name = "service_expected_outcome_snapshot", columnDefinition = "TEXT")
    private String serviceExpectedOutcomeSnapshot;

    @Column(name = "service_duration_snapshot")
    private Integer serviceDurationSnapshot;

    @Column(name = "service_is_free_snapshot")
    private Boolean serviceIsFreeSnapshot;

    @Column(name = "service_price_scoin_snapshot")
    private Integer servicePriceScoinSnapshot;

    @Version
    @Column(nullable = false)
    @Builder.Default
    private Integer version = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @PrePersist
    protected void onCreate() {
        createdAt = utcNow();
        updatedAt = createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = utcNow();
    }

    private LocalDateTime utcNow() {
        return LocalDateTime.ofInstant(DateTimeUtil.getClock().instant(), ZoneOffset.UTC);
    }
}
