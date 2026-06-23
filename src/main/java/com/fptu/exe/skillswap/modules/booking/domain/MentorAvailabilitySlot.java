package com.fptu.exe.skillswap.modules.booking.domain;

import com.fptu.exe.skillswap.shared.util.DateTimeUtil;

import com.fptu.exe.skillswap.modules.mentor.domain.MentorProfile;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorService;
import com.fptu.exe.skillswap.shared.persistence.GeneratedUuidV7;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "mentor_availability_slots", indexes = {
    @Index(name = "idx_availability_mentor_id", columnList = "mentor_user_id"),
    @Index(name = "idx_availability_start_time", columnList = "start_time"),
    @Index(name = "idx_availability_end_time", columnList = "end_time"),
    @Index(name = "idx_availability_booked", columnList = "is_booked"),
    @Index(name = "idx_availability_active", columnList = "is_active")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MentorAvailabilitySlot {

    @Id
    @GeneratedUuidV7
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "mentor_user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_availability_mentor"))
    private MentorProfile mentorProfile;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_id", nullable = false, foreignKey = @ForeignKey(name = "fk_availability_service"))
    private MentorService service;

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalDateTime endTime;

    @Column(nullable = false, length = 80)
    @Builder.Default
    private String timezone = "Asia/Ho_Chi_Minh";

    @Column(name = "is_booked", nullable = false)
    @Builder.Default
    private boolean isBooked = false;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean isActive = true;

    @Column(name = "recurrence_rule", columnDefinition = "TEXT")
    private String recurrenceRule;

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




