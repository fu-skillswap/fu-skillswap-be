package com.fptu.exe.skillswap.modules.blog.port;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/** Public, read-only author projection used by blog consumers. */
public interface BlogAuthorQueryPort {
    Map<UUID, AuthorSummary> findAuthors(Collection<UUID> authorUserIds);

    record AuthorSummary(UUID userId, String displayName, String avatarUrl, boolean active) { }
}
