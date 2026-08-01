package com.fptu.exe.skillswap.modules.mentor.dto.response;

import java.time.LocalDateTime;
import java.util.List;

/** Public-only article aggregate. It deliberately excludes premium and authenticated content. */
public record MentorAuthorityContentResponse(
        long publishedArticleCount,
        LocalDateTime latestPublishedAt,
        List<MentorPublicArticlePreviewResponse> recentPublicArticles
) {}
