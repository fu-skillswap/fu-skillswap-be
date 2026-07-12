package com.fptu.exe.skillswap.modules.blog.dto;

public record BlogEngagementState(
        boolean likedByCurrentUser,
        boolean bookmarkedByCurrentUser
) {
    public static BlogEngagementState empty() {
        return new BlogEngagementState(false, false);
    }
}
