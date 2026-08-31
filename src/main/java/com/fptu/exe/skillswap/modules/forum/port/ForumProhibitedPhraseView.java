package com.fptu.exe.skillswap.modules.forum.port;

import java.time.LocalDateTime;
import java.util.UUID;

/** Public admin projection for a forum moderation rule. */
public record ForumProhibitedPhraseView(UUID ruleId, String phrase, boolean isActive, Integer version,
                                       UUID createdByUserId, LocalDateTime createdAt, LocalDateTime updatedAt) { }
