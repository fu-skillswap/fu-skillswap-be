package com.fptu.exe.skillswap.modules.blog.port;

/** Immutable social-preview projection; it deliberately does not expose the web DTO or entity. */
public record BlogShareMetadata(
        String title,
        String excerpt,
        String imageUrl
) {
}
