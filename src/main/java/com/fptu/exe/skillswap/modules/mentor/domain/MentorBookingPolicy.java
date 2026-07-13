package com.fptu.exe.skillswap.modules.mentor.domain;

import com.fptu.exe.skillswap.shared.util.DateTimeUtil;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "mentor_booking_policies")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MentorBookingPolicy {

    @Id
    @Column(name = "mentor_user_id", nullable = false, updatable = false)
    private UUID mentorUserId;

    @Column(name = "minimum_booking_lead_time_minutes", nullable = false)
    @Builder.Default
    private Integer minimumBookingLeadTimeMinutes = 120;

    @Column(name = "maximum_booking_horizon_days", nullable = false)
    @Builder.Default
    private Integer maximumBookingHorizonDays = 30;

    @Column(name = "timezone", nullable = false, length = 64)
    @Builder.Default
    private String timezone = DateTimeUtil.ZONE_HCM;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @jakarta.persistence.PrePersist
    protected void onCreate() {
        createdAt = DateTimeUtil.now();
        updatedAt = DateTimeUtil.now();
        if (minimumBookingLeadTimeMinutes == null) {
            minimumBookingLeadTimeMinutes = 120;
        }
        if (maximumBookingHorizonDays == null) {
            maximumBookingHorizonDays = 30;
        }
        if (timezone == null || timezone.isBlank()) {
            timezone = DateTimeUtil.ZONE_HCM;
        }
    }

    @jakarta.persistence.PreUpdate
    protected void onUpdate() {
        updatedAt = DateTimeUtil.now();
    }
}
