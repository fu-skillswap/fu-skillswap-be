package com.fptu.exe.skillswap.modules.admin.domain;

import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.shared.persistence.GeneratedUuidV7;
import com.fptu.exe.skillswap.shared.util.DateTimeUtil;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "admin_case_assignments", uniqueConstraints = {
        @UniqueConstraint(name = "uq_admin_case_assignments_case", columnNames = {"case_type", "case_id"})
}, indexes = {
        @Index(name = "idx_admin_case_assignments_admin_id", columnList = "assigned_admin_user_id"),
        @Index(name = "idx_admin_case_assignments_case", columnList = "case_type, case_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminCaseAssignment {

    @Id
    @GeneratedUuidV7
    private UUID id;

    @Column(name = "case_type", nullable = false, length = 80)
    private String caseType;

    @Column(name = "case_id", nullable = false)
    private UUID caseId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assigned_admin_user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_admin_case_assignments_admin"))
    private User assignedAdminUser;

    @Column(name = "assigned_at", nullable = false)
    private LocalDateTime assignedAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = DateTimeUtil.now();
        assignedAt = assignedAt == null ? now : assignedAt;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = DateTimeUtil.now();
    }
}
