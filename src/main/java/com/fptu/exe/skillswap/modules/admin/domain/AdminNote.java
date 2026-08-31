package com.fptu.exe.skillswap.modules.admin.domain;

import com.fptu.exe.skillswap.shared.util.DateTimeUtil;

import com.fptu.exe.skillswap.shared.persistence.GeneratedUuidV7;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "admin_notes", indexes = {
    @Index(name = "idx_admin_notes_target", columnList = "target_type, target_id"),
    @Index(name = "idx_admin_notes_admin_id", columnList = "admin_user_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminNote {

    @Id
    @GeneratedUuidV7
    private UUID id;

    @Column(name = "target_type", nullable = false, length = 80)
    private String targetType;

    @Column(name = "target_id", nullable = false)
    private UUID targetId;

    @Column(name = "admin_user_id", nullable = false)
    private UUID adminUserId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = DateTimeUtil.now();
    }
}




