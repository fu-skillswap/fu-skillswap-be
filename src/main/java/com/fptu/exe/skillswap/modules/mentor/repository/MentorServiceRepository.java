package com.fptu.exe.skillswap.modules.mentor.repository;

import com.fptu.exe.skillswap.modules.mentor.domain.MentorService;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MentorServiceRepository extends JpaRepository<MentorService, UUID> {

    List<MentorService> findByMentorProfileUserIdAndIsActiveTrueOrderByCreatedAtAsc(UUID mentorUserId);

    Optional<MentorService> findByIdAndMentorProfileUserIdAndIsActiveTrue(UUID id, UUID mentorUserId);
}
