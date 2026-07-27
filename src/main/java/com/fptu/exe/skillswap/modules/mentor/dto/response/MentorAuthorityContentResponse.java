package com.fptu.exe.skillswap.modules.mentor.dto.response;

import java.time.LocalDateTime;

/** Public-only article aggregate. It deliberately excludes premium and authenticated content. */
public record MentorAuthorityContentResponse(long publishedArticleCount, LocalDateTime latestPublishedAt) {}
