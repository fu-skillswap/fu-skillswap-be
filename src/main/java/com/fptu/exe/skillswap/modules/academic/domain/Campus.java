package com.fptu.exe.skillswap.modules.academic.domain;

import com.fptu.exe.skillswap.shared.persistence.GeneratedUuidV7;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "campuses", uniqueConstraints = {
    @UniqueConstraint(name = "uq_campuses_code", columnNames = {"code"})
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Campus {

    @Id
    @GeneratedUuidV7
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, unique = true)
    private CampusCode code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 100)
    private String city;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean isActive = true;
}
