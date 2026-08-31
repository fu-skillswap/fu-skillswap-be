package com.fptu.exe.skillswap.modules.forum.support;

import java.util.UUID;

/** Test fixture providing standardized forum post and prohibited phrase snapshots. */
public final class ForumTestFixture {

    private ForumTestFixture() {}

    public static UUID randomPostId() {
        return UUID.randomUUID();
    }

    public static ForumPostSnapshot createPostSnapshot(UUID authorId) {
        return new ForumPostSnapshot(
                UUID.randomUUID(),
                authorId != null ? authorId : UUID.randomUUID(),
                "How to design modulithic architectures in Spring Boot?",
                "Discussion on module boundaries, ports, and event communication.",
                "PUBLISHED"
        );
    }

    public record ForumPostSnapshot(
            UUID postId,
            UUID authorUserId,
            String title,
            String content,
            String status
    ) {}
}
