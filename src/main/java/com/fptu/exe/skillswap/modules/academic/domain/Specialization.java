package com.fptu.exe.skillswap.modules.academic.domain;

import com.fptu.exe.skillswap.shared.persistence.GeneratedUuidV7;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "specializations", uniqueConstraints = {
    @UniqueConstraint(name = "uq_specializations_code", columnNames = {"code"})
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Specialization {

    @Id
    @GeneratedUuidV7
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "program_id", nullable = false, foreignKey = @ForeignKey(name = "fk_specializations_program"))
    private AcademicProgram program;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(name = "name_vi", nullable = false, length = 200)
    private String nameVi;

    @Column(name = "name_en", length = 200)
    private String nameEn;

    @Column(name = "is_expected", nullable = false)
    @Builder.Default
    private boolean isExpected = false;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean isActive = true;
}
