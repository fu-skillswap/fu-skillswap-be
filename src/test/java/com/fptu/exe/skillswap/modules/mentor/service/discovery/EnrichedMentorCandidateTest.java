package com.fptu.exe.skillswap.modules.mentor.service.discovery;

import com.fptu.exe.skillswap.modules.mentor.repository.MentorDiscoveryQueryRow;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EnrichedMentorCandidateTest {

    @Test
    void shouldNormalizeMissingEnrichmentToEmptyData() {
        MentorDiscoveryQueryRow row = new MentorDiscoveryQueryRow(
                UUID.randomUUID(), "Mentor", null, "Headline", "Expertise", "Bio",
                1, 1, 1, true, null, 0, 0, null,
                null, null, null, null, null, null, null, false,
                0, 0, 0, null, null
        );

        EnrichedMentorCandidate candidate = new EnrichedMentorCandidate(row, null);

        assertEquals(MentorEnrichedData.empty(), candidate.enrichment());
    }

    @Test
    void shouldRequireMentorRow() {
        assertThrows(NullPointerException.class, () -> new EnrichedMentorCandidate(null, null));
    }
}
