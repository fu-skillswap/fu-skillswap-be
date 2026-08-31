package com.fptu.exe.skillswap.modules.blog.port;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface BlogQueryPort {
    long getMentorPublicArticleCount(UUID mentorUserId);
    LocalDateTime getMentorLatestPublishedAt(UUID mentorUserId);
    List<BlogMentorArticlePreview> findMentorPublicProfilePreviews(UUID mentorUserId, int limit);
}
