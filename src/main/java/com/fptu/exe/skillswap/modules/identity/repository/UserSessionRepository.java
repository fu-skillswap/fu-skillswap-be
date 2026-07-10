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
}
