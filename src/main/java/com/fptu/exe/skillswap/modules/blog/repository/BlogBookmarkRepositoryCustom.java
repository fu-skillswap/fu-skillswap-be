package com.fptu.exe.skillswap.modules.blog.repository;

import com.fptu.exe.skillswap.modules.blog.domain.BlogBookmark;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface BlogBookmarkRepositoryCustom {
    List<BlogBookmark> findBookmarkWindow(UUID userId, LocalDateTime cursorCreatedAt, UUID cursorPostId, int fetchLimit);
}
