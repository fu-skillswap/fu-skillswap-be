package com.fptu.exe.skillswap.modules.identity.domain;

import com.fptu.exe.skillswap.shared.util.DateTimeUtil;

import com.fptu.exe.skillswap.shared.persistence.GeneratedUuidV7;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "user_sessions", indexes = {
    @Index(name = "idx_user_sessions_user_id", columnList = "user_id"),
    @Index(name = "idx_user_sessions_token", columnList = "refresh_token_hash")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSession {

    @Id
    @GeneratedUuidV7
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_user_sessions_user"))
    private User user;

    @Column(name = "refresh_token_hash", nullable = false, columnDefinition = "TEXT")
    private String refreshTokenHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "session_state", nullable = false, length = 30)
    @Builder.Default
    private UserSessionState sessionState = UserSessionState.ACTIVE;

    @Column(name = "grace_expires_at")
    private LocalDateTime graceExpiresAt;

    @Column(name = "grace_replacement_session_id")
    private UUID graceReplacementSessionId;

    @Column(name = "grace_replay_ciphertext", columnDefinition = "TEXT")
    private String graceReplayCiphertext;

    @Column(name = "device_info", columnDefinition = "TEXT")
    private String deviceInfo;

    @Column(name = "ip_address", length = 100)
    private String ipAddress;

    @Column(name = "is_revoked", nullable = false)
    @Builder.Default
    private boolean isRevoked = false;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (sessionState == null) {
            sessionState = UserSessionState.ACTIVE;
        }
        createdAt = DateTimeUtil.now();
    }
}




