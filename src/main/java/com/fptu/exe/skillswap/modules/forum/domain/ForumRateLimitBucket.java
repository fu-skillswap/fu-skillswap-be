package com.fptu.exe.skillswap.modules.forum.domain;

import com.fptu.exe.skillswap.shared.persistence.GeneratedUuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/** Persistent fixed-window counter shared by all application instances. */
@Entity
@Table(name = "forum_rate_limit_buckets",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_forum_rate_limit_bucket_key_window",
                columnNames = {"bucket_key", "window_started_at"}
        ),
        indexes = @Index(name = "idx_forum_rate_limit_buckets_expires", columnList = "window_expires_at"))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ForumRateLimitBucket {

    @Id
    @GeneratedUuidV7
    private UUID id;

    @Column(name = "bucket_key", nullable = false, length = 255)
    private String bucketKey;

    @Column(name = "window_started_at", nullable = false)
    private LocalDateTime windowStartedAt;

    @Column(name = "window_expires_at", nullable = false)
    private LocalDateTime windowExpiresAt;

    @Column(name = "request_count", nullable = false)
    private long requestCount;
}
