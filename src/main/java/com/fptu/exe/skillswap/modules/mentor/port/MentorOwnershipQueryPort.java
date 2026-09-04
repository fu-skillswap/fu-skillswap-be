package com.fptu.exe.skillswap.modules.mentor.port;

import java.util.UUID;

/** Narrow mentor ownership check for modules that authorize mentor-owned resources. */
public interface MentorOwnershipQueryPort {
    boolean isOwnedBy(UUID mentorProfileId, UUID userId);
    boolean isActiveOwner(UUID mentorProfileId, UUID userId);
}
