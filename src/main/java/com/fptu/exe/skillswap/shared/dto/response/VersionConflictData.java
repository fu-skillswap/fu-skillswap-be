package com.fptu.exe.skillswap.shared.dto.response;

import java.util.UUID;

/** Thông tin optimistic locking chung, trả cùng response 409. */
public record VersionConflictData(
        UUID postId,
        Integer expectedVersion,
        Integer currentVersion
) {
}
