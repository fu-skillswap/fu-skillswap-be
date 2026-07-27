package com.fptu.exe.skillswap.shared.dto.response;

import java.util.UUID;

/** Generic optimistic-concurrency details returned with a 409 response. */
public record VersionConflictData(
        UUID postId,
        Integer expectedVersion,
        Integer currentVersion
) {
}
