package com.fptu.exe.skillswap.modules.catalog.domain;

import com.fptu.exe.skillswap.shared.persistence.GeneratedUuidV7;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name="administrative_provinces", uniqueConstraints=@UniqueConstraint(name="uq_administrative_provinces_code", columnNames="code"), indexes=@Index(name="idx_administrative_provinces_name", columnList="normalized_name"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AdministrativeProvince {
    @Id @GeneratedUuidV7 private UUID id;
    @Column(nullable=false, length=2) private String code;
    @Column(nullable=false, length=120) private String name;
    @Column(name="normalized_name", nullable=false, length=120) private String normalizedName;
    @Column(name="unit_type", nullable=false, length=30) private String unitType;
    @Column(nullable=false) @Builder.Default private boolean active=true;
    @Column(name="sort_order", nullable=false) private int sortOrder;
    @Column(name="effective_from", nullable=false) private LocalDate effectiveFrom;
    @Column(name="created_at", nullable=false, updatable=false) private LocalDateTime createdAt;
    @Column(name="updated_at", nullable=false) private LocalDateTime updatedAt;
    @PrePersist void create(){ createdAt=LocalDateTime.now(); updatedAt=createdAt; }
    @PreUpdate void update(){ updatedAt=LocalDateTime.now(); }
}
