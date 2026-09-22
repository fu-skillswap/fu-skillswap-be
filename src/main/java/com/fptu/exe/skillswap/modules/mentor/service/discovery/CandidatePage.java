package com.fptu.exe.skillswap.modules.mentor.service.discovery;

import java.util.List;
import java.util.UUID;

/** One database-paginated search page and its matching total count. */
public record CandidatePage(List<UUID> candidateIds, long totalCount) {
    public CandidatePage {
        candidateIds = candidateIds == null ? List.of() : List.copyOf(candidateIds);
    }

    public boolean isEmpty() {
        return candidateIds.isEmpty();
    }
}
