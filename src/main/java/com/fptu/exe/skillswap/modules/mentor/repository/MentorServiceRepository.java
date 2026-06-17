package com.fptu.exe.skillswap.modules.mentor.repository;

import com.fptu.exe.skillswap.modules.mentor.domain.MentorService;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MentorServiceRepository extends JpaRepository<MentorService, UUID> {

    @EntityGraph(attributePaths = {"helpTopics"})
    List<MentorService> findByMentorProfileUserIdAndIsActiveTrueOrderByCreatedAtAsc(UUID mentorUserId);

    @EntityGraph(attributePaths = {"helpTopics"})
    List<MentorService> findByMentorProfileUserIdInAndIsActiveTrueOrderByCreatedAtAsc(List<UUID> mentorUserIds);

    @EntityGraph(attributePaths = {"helpTopics"})
    List<MentorService> findByMentorProfileUserIdOrderByCreatedAtAsc(UUID mentorUserId);

    @EntityGraph(attributePaths = {"helpTopics"})
    Optional<MentorService> findByIdAndMentorProfileUserId(UUID id, UUID mentorUserId);

    @EntityGraph(attributePaths = {"helpTopics"})
    Optional<MentorService> findByIdAndMentorProfileUserIdAndIsActiveTrue(UUID id, UUID mentorUserId);
}
