package com.fptu.exe.skillswap.modules.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;

import java.io.Serializable;
import java.util.UUID;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class SpecializationTagId implements Serializable {

    @Column(name = "specialization_id")
    private UUID specializationId;

    @Column(name = "tag_id")
    private UUID tagId;
}
