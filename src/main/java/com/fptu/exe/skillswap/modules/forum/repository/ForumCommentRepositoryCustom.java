package com.fptu.exe.skillswap.modules.forum.repository;

import com.fptu.exe.skillswap.modules.forum.domain.ForumComment;
import com.fptu.exe.skillswap.modules.forum.domain.ForumCommentStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface ForumCommentRepositoryCustom {

    List<ForumComment> findVisibleCommentsWindow(
            UUID postId,
            ForumCommentStatus status,
            LocalDateTime cursorCreatedAt,
            UUID cursorCommentId,
            int fetchLimit
    );

    List<ForumComment> findAdminCommentsWindow(
            ForumCommentStatus status,
            UUID postId,
            UUID authorId,
            String keywordPattern,
            LocalDateTime cursorCreatedAt,
            UUID cursorCommentId,
            int fetchLimit
    );
}
