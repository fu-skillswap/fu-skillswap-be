package com.fptu.exe.skillswap.modules.mentor.port;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

public interface MentorContentAccessPort {
    Map<UUID, MentorBlogAuthorSummary> getBlogAuthorSummaries(Collection<UUID> userIds);
    boolean isPubliclyReadableMentor(UUID userId);
}
