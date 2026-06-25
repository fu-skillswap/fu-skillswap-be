package com.fptu.exe.skillswap.modules.booking.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.UUID;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class AvailabilitySlotServiceId implements Serializable {

    @Column(name = "slot_id", nullable = false)
    private UUID slotId;

    @Column(name = "service_id", nullable = false)
    private UUID serviceId;
}
