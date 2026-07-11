package com.fptu.exe.skillswap.modules.forum.repository;

import com.fptu.exe.skillswap.modules.forum.domain.ForumActionLog;
import com.fptu.exe.skillswap.modules.forum.domain.ForumActionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.UUID;

@Repository
public interface ForumActionLogRepository extends JpaRepository<ForumActionLog, UUID> {

    long countByUserIdAndActionTypeAndCreatedAtAfter(UUID userId, ForumActionType actionType, LocalDateTime createdAt);
}
