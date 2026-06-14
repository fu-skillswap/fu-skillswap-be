package com.fptu.exe.skillswap.modules.mentor.repository;

import com.fptu.exe.skillswap.modules.mentor.domain.MentorVerificationRequestEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MentorVerificationRequestEventRepository extends JpaRepository<MentorVerificationRequestEvent, UUID> {

    List<MentorVerificationRequestEvent> findByRequestIdOrderByCreatedAtAsc(UUID requestId);
}
