package com.fptu.exe.skillswap.modules.mentor.service.discovery;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MentorMatchingContextTest {

    @Test
    void shouldRequireIdentityEvaluationTimeAndAlgorithmVersion() {
        UUID userId = UUID.randomUUID();
        LocalDateTime evaluatedAt = LocalDateTime.of(2026, 8, 6, 15, 0);

        MentorMatchingContext context = new MentorMatchingContext(userId, null, evaluatedAt, " structured-v1 ");

        assertEquals(userId, context.menteeUserId());
        assertEquals(evaluatedAt, context.evaluatedAt());
        assertEquals("structured-v1", context.algorithmVersion());
    }

    @Test
    void shouldRejectMissingRequiredFields() {
        LocalDateTime evaluatedAt = LocalDateTime.of(2026, 8, 6, 15, 0);

        assertThrows(NullPointerException.class,
                () -> new MentorMatchingContext(null, null, evaluatedAt, "structured-v1"));
        assertThrows(NullPointerException.class,
                () -> new MentorMatchingContext(UUID.randomUUID(), null, null, "structured-v1"));
        assertThrows(IllegalArgumentException.class,
                () -> new MentorMatchingContext(UUID.randomUUID(), null, evaluatedAt, " "));
    }
}
