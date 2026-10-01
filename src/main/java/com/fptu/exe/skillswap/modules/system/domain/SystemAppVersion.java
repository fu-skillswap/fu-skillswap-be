package com.fptu.exe.skillswap.modules.system.domain;

import com.fptu.exe.skillswap.shared.util.DateTimeUtil;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "system_app_versions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemAppVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 32)
    private String platform;

    @Column(name = "min_version_code", nullable = false)
    @Builder.Default
    private int minVersionCode = 1;

    @Column(name = "latest_version_code", nullable = false)
    @Builder.Default
    private int latestVersionCode = 1;

    @Column(name = "is_maintenance", nullable = false)
    @Builder.Default
    private boolean isMaintenance = false;

    @Column(name = "update_url", length = 512)
    private String updateUrl;

    @Column(name = "maintenance_message", length = 512)
    private String maintenanceMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = DateTimeUtil.now();
        }
        if (updatedAt == null) {
            updatedAt = DateTimeUtil.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = DateTimeUtil.now();
    }
}
