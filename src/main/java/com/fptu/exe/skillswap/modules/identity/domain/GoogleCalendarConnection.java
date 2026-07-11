package com.fptu.exe.skillswap.modules.identity.domain;

import com.fptu.exe.skillswap.shared.persistence.GeneratedUuidV7;
import com.fptu.exe.skillswap.shared.util.DateTimeUtil;
import jakarta.persistence.*;
import lombok.*;

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

    @Column(name = "last_sync_error_code", length = 100)
    private String lastSyncErrorCode;

    @Column(name = "last_sync_error_message", columnDefinition = "TEXT")
    private String lastSyncErrorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = DateTimeUtil.now();
        updatedAt = DateTimeUtil.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = DateTimeUtil.now();
    }
}
