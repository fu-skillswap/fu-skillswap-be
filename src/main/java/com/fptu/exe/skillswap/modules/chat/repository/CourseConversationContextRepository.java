package com.fptu.exe.skillswap.modules.chat.repository;

import com.fptu.exe.skillswap.modules.chat.domain.CourseConversationContext;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface CourseConversationContextRepository extends JpaRepository<CourseConversationContext, UUID> {

    Optional<CourseConversationContext> findByCourseIdAndMenteeUserId(UUID courseId, UUID menteeUserId);

    Optional<CourseConversationContext> findByConversationId(UUID conversationId);

    List<CourseConversationContext> findByConversationIdIn(Collection<UUID> conversationIds);
}
