package com.fptu.exe.skillswap.modules.academic.domain;

import com.fptu.exe.skillswap.shared.persistence.GeneratedUuidV7;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "academic_programs", uniqueConstraints = {
    @UniqueConstraint(name = "uq_academic_programs_code", columnNames = {"code"})
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AcademicProgram {

    @Id
    @GeneratedUuidV7
    private UUID id;

    @Column(nullable = false, unique = true, length = 30)
    private String code;

    @Column(name = "name_vi", nullable = false, length = 150)
    private String nameVi;

    @Column(name = "name_en", length = 150)
    private String nameEn;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean isActive = true;
}
