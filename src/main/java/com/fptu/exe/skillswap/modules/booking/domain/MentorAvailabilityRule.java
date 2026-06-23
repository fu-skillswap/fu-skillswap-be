package com.fptu.exe.skillswap.modules.booking.domain;

import com.fptu.exe.skillswap.shared.util.DateTimeUtil;

import com.fptu.exe.skillswap.modules.mentor.domain.MentorProfile;
import com.fptu.exe.skillswap.shared.persistence.GeneratedUuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "mentor_availability_rules", indexes = {
        @Index(name = "idx_availability_rules_mentor_active", columnList = "mentor_user_id, is_active"),
        @Index(name = "idx_availability_rules_date_range", columnList = "effective_from, effective_to"),
        @Index(name = "idx_availability_rules_repeat_type", columnList = "repeat_type")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MentorAvailabilityRule {

    @Id
    @GeneratedUuidV7
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "mentor_user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_availability_rules_mentor"))
    private MentorProfile mentorProfile;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_id", nullable = false, foreignKey = @ForeignKey(name = "fk_availability_rules_service"))
    private com.fptu.exe.skillswap.modules.mentor.domain.MentorService service;

    @Enumerated(EnumType.STRING)
    @Column(name = "rule_type", nullable = false, length = 20)
    private AvailabilityRuleType ruleType;

    @Enumerated(EnumType.STRING)
    @Column(name = "repeat_type", nullable = false, length = 20)
    private AvailabilityRepeatType repeatType;

    @Column(name = "days_of_week", length = 80)
    private String daysOfWeek;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "start_time")
    private LocalTime startTime;

    @Column(name = "end_time")
    private LocalTime endTime;

    @Column(nullable = false, length = 80)
    @Builder.Default
    private String timezone = "Asia/Ho_Chi_Minh";

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(length = 200)
    private String note;

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




