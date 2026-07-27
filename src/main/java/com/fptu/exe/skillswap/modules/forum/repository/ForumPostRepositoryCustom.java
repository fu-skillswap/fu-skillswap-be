package com.fptu.exe.skillswap.modules.forum.repository;

import com.fptu.exe.skillswap.modules.forum.domain.ForumPost;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.time.LocalDateTime;
import java.util.UUID;

public interface ForumPostRepositoryCustom {

    List<ForumPost> findWindow(Specification<ForumPost> specification, int fetchLimit);

    List<ForumPost> findProgramPrioritizedWindow(
            UUID programId,
            Integer afterPriority,
            LocalDateTime afterLastActivityAt,
            UUID afterPostId,
            int fetchLimit
    );
}
