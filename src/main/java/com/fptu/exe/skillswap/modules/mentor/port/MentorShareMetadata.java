package com.fptu.exe.skillswap.modules.mentor.port;

/** Immutable social-preview projection; it deliberately does not expose the web DTO or entity. */
public record MentorShareMetadata(
        String displayName,
        String headline,
        String avatarUrl
) {
}
