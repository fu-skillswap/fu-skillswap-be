package com.fptu.exe.skillswap.modules.mentor.repository;

import com.fptu.exe.skillswap.modules.mentor.domain.MentorFeaturedProject;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MentorFeaturedProjectRepository extends JpaRepository<MentorFeaturedProject, UUID> {

    List<MentorFeaturedProject> findByMentorProfileUserIdOrderByDisplayOrderAscCreatedAtAsc(UUID mentorUserId);

    List<MentorFeaturedProject> findByMentorProfileUserIdInOrderByMentorProfileUserIdAscDisplayOrderAscCreatedAtAsc(Collection<UUID> mentorUserIds);

    Optional<MentorFeaturedProject> findByIdAndMentorProfileUserId(UUID id, UUID mentorUserId);
}
