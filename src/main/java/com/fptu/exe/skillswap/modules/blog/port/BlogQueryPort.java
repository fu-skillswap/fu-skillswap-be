package com.fptu.exe.skillswap.modules.blog.port;

import com.fptu.exe.skillswap.modules.blog.dto.BlogPostReaderDetailResponse;
import com.fptu.exe.skillswap.modules.blog.dto.response.MentorPublicArticlePreviewResponse;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface BlogQueryPort {
    List<MentorPublicArticlePreviewResponse> findRecentArticlesByMentor(UUID mentorUserId, int limit);
    BlogPostReaderDetailResponse getBySlug(String slug);
    long countPublishedArticlesByMentor(UUID mentorUserId);
    LocalDateTime getLatestPublishedArticleDate(UUID mentorUserId);
}
