package com.fptu.exe.skillswap.modules.forum.event;

import java.util.UUID;

/** Signals that the immutable active prohibited-phrase snapshot changed. */
public record ForumProhibitedPhraseChangedEvent(UUID ruleId, String action) {
}
