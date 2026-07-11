package com.fptu.exe.skillswap.modules.mentor.repository;

import com.fptu.exe.skillswap.modules.mentor.domain.MentorAchievement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MentorAchievementRepository extends JpaRepository<MentorAchievement, UUID> {

    List<MentorAchievement> findByMentorProfileUserIdOrderByDisplayOrderAscCreatedAtAsc(UUID mentorUserId);

    List<MentorAchievement> findByMentorProfileUserIdInOrderByMentorProfileUserIdAscDisplayOrderAscCreatedAtAsc(Collection<UUID> mentorUserIds);

    Optional<MentorAchievement> findByIdAndMentorProfileUserId(UUID id, UUID mentorUserId);
}
