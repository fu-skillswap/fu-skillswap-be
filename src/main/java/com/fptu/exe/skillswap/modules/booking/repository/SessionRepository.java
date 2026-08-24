package com.fptu.exe.skillswap.modules.booking.repository;

import com.fptu.exe.skillswap.modules.booking.domain.Session;
import com.fptu.exe.skillswap.modules.booking.domain.SessionSourceType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SessionRepository extends JpaRepository<Session, UUID> {
    Optional<Session> findBySourceTypeAndSourceId(SessionSourceType sourceType, UUID sourceId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select session from Session session where session.sourceType = :sourceType and session.sourceId = :sourceId")
    Optional<Session> findBySourceTypeAndSourceIdForUpdate(@Param("sourceType") SessionSourceType sourceType,
                                                            @Param("sourceId") UUID sourceId);

    List<Session> findBySourceTypeAndSourceIdIn(SessionSourceType sourceType, Collection<UUID> sourceIds);
}
