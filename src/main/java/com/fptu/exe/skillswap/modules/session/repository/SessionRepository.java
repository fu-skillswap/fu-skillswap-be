package com.fptu.exe.skillswap.modules.session.repository;

import com.fptu.exe.skillswap.modules.session.domain.Session;
import com.fptu.exe.skillswap.modules.session.domain.SessionSourceType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SessionRepository extends JpaRepository<Session, UUID> {
    Optional<Session> findBySourceTypeAndSourceId(SessionSourceType sourceType, UUID sourceId);
}
