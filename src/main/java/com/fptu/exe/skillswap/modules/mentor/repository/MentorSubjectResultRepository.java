package com.fptu.exe.skillswap.modules.mentor.repository;

import com.fptu.exe.skillswap.modules.mentor.domain.MentorSubjectResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface MentorSubjectResultRepository extends JpaRepository<MentorSubjectResult, UUID> {

    List<MentorSubjectResult> findByMentorProfileUserIdOrderByDisplayOrderAscCreatedAtAsc(UUID mentorUserId);

    List<MentorSubjectResult> findByMentorProfileUserIdInOrderByMentorProfileUserIdAscDisplayOrderAscCreatedAtAsc(Collection<UUID> mentorUserIds);

    void deleteByMentorProfileUserId(UUID mentorUserId);
}
