package com.fptu.exe.skillswap.modules.catalog.domain;

import com.fptu.exe.skillswap.shared.persistence.GeneratedUuidV7;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name="educational_institutions", uniqueConstraints={@UniqueConstraint(name="uq_educational_institutions_slug", columnNames="slug"), @UniqueConstraint(name="uq_educational_institutions_name_province", columnNames={"normalized_name","province_id"})}, indexes=@Index(name="idx_educational_institutions_province", columnList="province_id,active,sort_order"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class EducationalInstitution {
    @Id @GeneratedUuidV7 private UUID id;
    @Column(nullable=false, length=120) private String slug;
    @Column(nullable=false, length=200) private String name;
    @Column(name="short_name", length=80) private String shortName;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="province_id", nullable=false, foreignKey=@ForeignKey(name="fk_educational_institutions_province")) private AdministrativeProvince province;
    @Column(name="institution_type", nullable=false, length=40) private String institutionType;
    @Column(name="normalized_name", nullable=false, length=200) private String normalizedName;
    @Column(nullable=false) @Builder.Default private boolean active=true;
    @Column(name="sort_order", nullable=false) private int sortOrder;
    @Column(name="created_at", nullable=false, updatable=false) private LocalDateTime createdAt;
    @Column(name="updated_at", nullable=false) private LocalDateTime updatedAt;
    @PrePersist void create(){ createdAt=LocalDateTime.now(); updatedAt=createdAt; }
    @PreUpdate void update(){ updatedAt=LocalDateTime.now(); }
}
