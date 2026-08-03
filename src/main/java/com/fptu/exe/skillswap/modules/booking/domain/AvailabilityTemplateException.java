package com.fptu.exe.skillswap.modules.booking.domain;

import com.fptu.exe.skillswap.shared.persistence.GeneratedUuidV7;
import com.fptu.exe.skillswap.shared.util.DateTimeUtil;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "availability_template_exceptions", uniqueConstraints = @UniqueConstraint(name = "uq_availability_template_exception_date", columnNames = {"template_id", "occurrence_date"}))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AvailabilityTemplateException {
    @Id
    @GeneratedUuidV7
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "template_id", nullable = false, foreignKey = @ForeignKey(name = "fk_availability_template_exception_template"))
    private AvailabilityTemplate template;

    @Column(name = "occurrence_date", nullable = false)
    private LocalDate occurrenceDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AvailabilityTemplateExceptionProvenance provenance;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_manual_slot_id", foreignKey = @ForeignKey(name = "fk_availability_template_exception_source_slot"))
    private MentorAvailabilitySlot sourceManualSlot;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() { createdAt = DateTimeUtil.now(); }
}
