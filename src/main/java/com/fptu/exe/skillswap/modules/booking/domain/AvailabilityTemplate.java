package com.fptu.exe.skillswap.modules.booking.domain;

import com.fptu.exe.skillswap.modules.mentor.domain.MentorProfile;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorService;
import com.fptu.exe.skillswap.shared.persistence.GeneratedUuidV7;
import com.fptu.exe.skillswap.shared.util.DateTimeUtil;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "availability_templates", indexes = {
        @Index(name = "idx_availability_templates_mentor_status", columnList = "mentor_user_id, configured_status, effective_from"),
        @Index(name = "idx_availability_templates_effective_dates", columnList = "effective_from, effective_to")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AvailabilityTemplate {

    @Id
    @GeneratedUuidV7
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "mentor_user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_availability_templates_mentor"))
    private MentorProfile mentorProfile;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(name = "weekdays", nullable = false, length = 80)
    private String weekdays;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(nullable = false, length = 80)
    @Builder.Default
    private String timezone = "Asia/Ho_Chi_Minh";

    @Column(length = 200)
    private String note;

    @Enumerated(EnumType.STRING)
    @Column(name = "configured_status", nullable = false, length = 20)
    @Builder.Default
    private AvailabilityTemplateConfiguredStatus configuredStatus = AvailabilityTemplateConfiguredStatus.ACTIVE;

    @Version
    @Column(name = "config_version", nullable = false)
    @Builder.Default
    private Integer configVersion = 1;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "availability_template_services",
            joinColumns = @JoinColumn(name = "template_id"),
            inverseJoinColumns = @JoinColumn(name = "service_id"))
    @Builder.Default
    private Set<MentorService> services = new LinkedHashSet<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = DateTimeUtil.now();
        updatedAt = DateTimeUtil.now();
        if (configVersion == null) configVersion = 1;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = DateTimeUtil.now();
    }
}
