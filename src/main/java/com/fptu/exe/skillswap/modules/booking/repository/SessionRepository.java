package com.fptu.exe.skillswap.modules.booking.repository;

import com.fptu.exe.skillswap.modules.booking.domain.Session;
import com.fptu.exe.skillswap.modules.booking.domain.SessionSourceType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SessionRepository extends JpaRepository<Session, UUID> {
    Optional<Session> findBySourceTypeAndSourceId(SessionSourceType sourceType, UUID sourceId);

    List<Session> findBySourceTypeAndSourceIdIn(SessionSourceType sourceType, Collection<UUID> sourceIds);
}
