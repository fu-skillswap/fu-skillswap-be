package com.fptu.exe.skillswap.modules.blog.repository;

import com.fptu.exe.skillswap.modules.blog.domain.BlogAudienceType;
import com.fptu.exe.skillswap.modules.blog.domain.BlogPost;
import com.fptu.exe.skillswap.modules.blog.domain.BlogPostStatus;
import com.fptu.exe.skillswap.modules.blog.domain.BlogVisibility;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface BlogPostRepositoryCustom {
    List<BlogPost> findPublicWindow(
            Collection<BlogVisibility> allowedVisibilities,
            UUID categoryId,
            UUID tagId,
            BlogAudienceType audienceType,
            String keywordPattern,
            LocalDateTime cursorPublishedAt,
            UUID cursorPostId,
            int fetchLimit
    );

    List<BlogPost> findAdminWindow(
            BlogPostStatus status,
            UUID authorUserId,
            UUID categoryId,
            UUID tagId,
            String keywordPattern,
            LocalDateTime cursorUpdatedAt,
            UUID cursorPostId,
            int fetchLimit
    );
}
