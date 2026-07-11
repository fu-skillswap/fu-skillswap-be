package com.fptu.exe.skillswap.modules.mentor.service.discovery;

import java.util.List;
import java.util.UUID;

public record CandidateWindow(
        List<UUID> candidateIds,
        long totalCount
) {
    public boolean isEmpty() {
        return candidateIds == null || candidateIds.isEmpty();
    }
}
