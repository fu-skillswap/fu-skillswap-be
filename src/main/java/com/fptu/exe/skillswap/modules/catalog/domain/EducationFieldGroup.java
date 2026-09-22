package com.fptu.exe.skillswap.modules.catalog.domain;

import com.fptu.exe.skillswap.shared.persistence.GeneratedUuidV7;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name="education_field_groups", uniqueConstraints=@UniqueConstraint(name="uq_education_field_groups_official_code", columnNames="official_code"), indexes=@Index(name="idx_education_field_groups_name", columnList="normalized_name"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class EducationFieldGroup {
    @Id @GeneratedUuidV7 private UUID id;
    @Column(name="official_code", nullable=false, length=5) private String officialCode;
    @Column(nullable=false, length=150) private String name;
    @Column(name="normalized_name", nullable=false, length=150) private String normalizedName;
    @ElementCollection(fetch=FetchType.EAGER) @CollectionTable(name="education_field_group_aliases", joinColumns=@JoinColumn(name="field_group_id", foreignKey=@ForeignKey(name="fk_education_field_alias_group"))) @Column(name="alias", nullable=false, length=150) @Builder.Default private List<String> aliases=new ArrayList<>();
    @Column(nullable=false) @Builder.Default private boolean active=true;
    @Column(name="sort_order", nullable=false) private int sortOrder;
    @Column(name="created_at", nullable=false, updatable=false) private LocalDateTime createdAt;
    @Column(name="updated_at", nullable=false) private LocalDateTime updatedAt;
    @PrePersist void create(){ createdAt=LocalDateTime.now(); updatedAt=createdAt; }
    @PreUpdate void update(){ updatedAt=LocalDateTime.now(); }
}
