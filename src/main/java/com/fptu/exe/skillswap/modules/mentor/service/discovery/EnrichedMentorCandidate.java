package com.fptu.exe.skillswap.modules.mentor.service.discovery;

import com.fptu.exe.skillswap.modules.mentor.repository.MentorDiscoveryQueryRow;

import java.util.Objects;

public record EnrichedMentorCandidate(
        MentorDiscoveryQueryRow row,
        MentorEnrichedData enrichment
) {
    public EnrichedMentorCandidate {
        Objects.requireNonNull(row, "row must not be null");
        enrichment = enrichment == null ? MentorEnrichedData.empty() : enrichment;
    }
}
