package com.fptu.exe.skillswap.modules.booking.domain;

import com.fptu.exe.skillswap.modules.mentor.domain.MentorService;
import com.fptu.exe.skillswap.shared.util.DateTimeUtil;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "availability_slot_services")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AvailabilitySlotService {

    @EmbeddedId
    private AvailabilitySlotServiceId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("slotId")
    @JoinColumn(name = "slot_id", nullable = false, foreignKey = @ForeignKey(name = "fk_availability_slot_services_slot"))
    private MentorAvailabilitySlot slot;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("serviceId")
    @JoinColumn(name = "service_id", nullable = false, foreignKey = @ForeignKey(name = "fk_availability_slot_services_service"))
    private MentorService service;

    @Builder.Default
    private LocalDateTime createdAt = DateTimeUtil.now();

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = DateTimeUtil.now();
        }
        if (id == null && slot != null && service != null) {
            id = new AvailabilitySlotServiceId(slot.getId(), service.getId());
        }
    }
}
