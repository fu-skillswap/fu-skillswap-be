package com.fptu.exe.skillswap.modules.blog.dto;

import java.util.UUID;

/** Minimal state needed to update a reader card after a like or bookmark mutation. */
public record BlogEngagementMutationResponse(
        UUID postId,
        boolean likedByCurrentUser,
        boolean bookmarkedByCurrentUser,
        long likeCount,
        long bookmarkCount
) {
}
