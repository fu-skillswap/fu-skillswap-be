package com.fptu.exe.skillswap.modules.identity.domain;

import com.fptu.exe.skillswap.modules.booking.service.BookingTime;
import com.fptu.exe.skillswap.shared.persistence.GeneratedUuidV7;
import com.fptu.exe.skillswap.shared.util.DateTimeUtil;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "oauth_accounts", uniqueConstraints = {
    @UniqueConstraint(name = "uq_oauth_accounts_provider_user", columnNames = {"provider", "provider_user_id"})
}, indexes = {
    @Index(name = "idx_oauth_accounts_user_id", columnList = "user_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OauthAccount {

    @Id
    @GeneratedUuidV7
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_oauth_accounts_user"))
    private User user;

    @Column(nullable = false, length = 50)
    private String provider;

    @Column(name = "provider_user_id", nullable = false)
    private String providerUserId;

    @Column(name = "provider_email")
    private String providerEmail;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "created_at_utc", nullable = false, updatable = false)
    private Instant createdAtUtc;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "updated_at_utc", nullable = false)
    private Instant updatedAtUtc;

    @PrePersist
    protected void onCreate() {
        if (createdAtUtc == null) {
            createdAtUtc = createdAt != null ? BookingTime.toInstant(createdAt) : DateTimeUtil.instantNow();
        }
        if (createdAt == null) {
            createdAt = BookingTime.fromInstant(createdAtUtc);
        }
        if (updatedAtUtc == null) {
            updatedAtUtc = updatedAt != null ? BookingTime.toInstant(updatedAt) : createdAtUtc;
        }
        if (updatedAt == null) {
            updatedAt = BookingTime.fromInstant(updatedAtUtc);
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAtUtc = DateTimeUtil.instantNow();
        updatedAt = BookingTime.fromInstant(updatedAtUtc);
    }
}
