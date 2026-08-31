package com.fptu.exe.skillswap.modules.mentor.port;

import java.util.List;
import java.util.UUID;

/** Public, reader-safe metadata required to render a mentor social preview. */
public interface MentorShareQueryPort {

    MentorShareMetadata findShareMetadata(UUID mentorUserId);

    /** User IDs of mentors currently visible in the public sitemap. */
    List<UUID> findPublicMentorUserIds();
}
