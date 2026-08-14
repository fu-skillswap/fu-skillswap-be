package com.fptu.exe.skillswap.modules.identity.repository;

import com.fptu.exe.skillswap.modules.identity.domain.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.LocalDateTime;

@Repository
public interface UserSessionRepository extends JpaRepository<UserSession, UUID> {
    Optional<UserSession> findByRefreshTokenHash(String refreshTokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select s
            from UserSession s
            join fetch s.user
            where s.refreshTokenHash = :refreshTokenHash
            """)
    Optional<UserSession> findByRefreshTokenHashForUpdate(@Param("refreshTokenHash") String refreshTokenHash);

    Optional<UserSession> findByGraceReplacementSessionId(UUID graceReplacementSessionId);

    List<UserSession> findByUserIdAndIsRevokedFalse(UUID userId);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query(value = """
            DELETE FROM user_sessions
            WHERE id IN (
                SELECT id
                FROM user_sessions
                WHERE (session_state = 'EXPIRED' AND expires_at < :expiredBefore)
                   OR (session_state = 'REVOKED' AND COALESCE(revoked_at, created_at) < :revokedBefore)
                ORDER BY created_at
                LIMIT :batchSize
            )
            """, nativeQuery = true)
    int deleteExpiredOrRevokedBatch(@org.springframework.data.repository.query.Param("expiredBefore") LocalDateTime expiredBefore,
                                    @org.springframework.data.repository.query.Param("revokedBefore") LocalDateTime revokedBefore,
                                    @org.springframework.data.repository.query.Param("batchSize") int batchSize);
}
