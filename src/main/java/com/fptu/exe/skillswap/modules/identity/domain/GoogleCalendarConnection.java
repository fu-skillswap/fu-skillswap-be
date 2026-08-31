package com.fptu.exe.skillswap.modules.identity.domain;

import com.fptu.exe.skillswap.shared.time.BusinessTime;
import com.fptu.exe.skillswap.shared.persistence.GeneratedUuidV7;
import com.fptu.exe.skillswap.shared.util.DateTimeUtil;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "google_calendar_connections", indexes = {
        @Index(name = "idx_google_calendar_connections_status", columnList = "connection_status")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GoogleCalendarConnection {

    @Id
    @GeneratedUuidV7
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_google_calendar_connections_user"))
    private User user;

    @Column(name = "google_subject", nullable = false, length = 255)
    private String googleSubject;

    @Column(name = "google_email", nullable = false, length = 255)
    private String googleEmail;

    @Column(name = "calendar_id", nullable = false, length = 255)
    private String calendarId;

    @Column(name = "access_token_ciphertext", nullable = false, columnDefinition = "TEXT")
    private String accessTokenCiphertext;

    @Column(name = "refresh_token_ciphertext", columnDefinition = "TEXT")
    private String refreshTokenCiphertext;

    @Column(name = "token_expires_at")
    private LocalDateTime tokenExpiresAt;

    @Column(name = "token_expires_at_utc")
    private Instant tokenExpiresAtUtc;

    @Column(name = "granted_scopes", columnDefinition = "TEXT")
    private String grantedScopes;

    @Column(name = "key_version", nullable = false)
    private Integer keyVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "connection_status", nullable = false, length = 50)
    private GoogleCalendarConnectionStatus connectionStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "last_sync_status", length = 50)
    private GoogleCalendarSyncStatus lastSyncStatus;

    @Column(name = "last_sync_at")
    private LocalDateTime lastSyncAt;

    @Column(name = "last_sync_at_utc")
    private Instant lastSyncAtUtc;

    @Column(name = "last_sync_error_code", length = 100)
    private String lastSyncErrorCode;

    @Column(name = "last_sync_error_message", columnDefinition = "TEXT")
    private String lastSyncErrorMessage;

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
            createdAtUtc = createdAt != null ? BusinessTime.toInstant(createdAt) : DateTimeUtil.instantNow();
        }
        if (createdAt == null) {
            createdAt = BusinessTime.fromInstant(createdAtUtc);
        }
        if (updatedAtUtc == null) {
            updatedAtUtc = updatedAt != null ? BusinessTime.toInstant(updatedAt) : createdAtUtc;
        }
        if (updatedAt == null) {
            updatedAt = BusinessTime.fromInstant(updatedAtUtc);
        }
        syncDualWriteFields();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAtUtc = DateTimeUtil.instantNow();
        updatedAt = BusinessTime.fromInstant(updatedAtUtc);
        syncDualWriteFields();
    }

    private void syncDualWriteFields() {
        if (tokenExpiresAtUtc != null && tokenExpiresAt == null) {
            tokenExpiresAt = BusinessTime.fromInstant(tokenExpiresAtUtc);
        } else if (tokenExpiresAt != null && tokenExpiresAtUtc == null) {
            tokenExpiresAtUtc = BusinessTime.toInstant(tokenExpiresAt);
        }

        if (lastSyncAtUtc != null && lastSyncAt == null) {
            lastSyncAt = BusinessTime.fromInstant(lastSyncAtUtc);
        } else if (lastSyncAt != null && lastSyncAtUtc == null) {
            lastSyncAtUtc = BusinessTime.toInstant(lastSyncAt);
        }
    }
}
