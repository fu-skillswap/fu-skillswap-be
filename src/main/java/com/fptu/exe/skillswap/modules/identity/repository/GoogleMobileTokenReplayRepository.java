package com.fptu.exe.skillswap.modules.identity.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Repository
@RequiredArgsConstructor
public class GoogleMobileTokenReplayRepository {

    private final JdbcTemplate jdbcTemplate;

    /**
     * Claims a Google Mobile token identity exactly once across all application instances.
     */
    public boolean claim(String googleSubject, long issuedAt, Instant expiresAt) {
        int inserted = jdbcTemplate.update("""
                INSERT INTO google_mobile_token_replays (google_sub, issued_at, expires_at)
                VALUES (?, ?, ?)
                ON CONFLICT (google_sub, issued_at) DO NOTHING
                """,
                googleSubject,
                issuedAt,
                OffsetDateTime.ofInstant(expiresAt, ZoneOffset.UTC)
        );
        return inserted == 1;
    }

    public int deleteExpired() {
        return jdbcTemplate.update("""
                DELETE FROM google_mobile_token_replays
                WHERE expires_at <= CURRENT_TIMESTAMP
                """
        );
    }
}
