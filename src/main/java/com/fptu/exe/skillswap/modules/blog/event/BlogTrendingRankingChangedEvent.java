package com.fptu.exe.skillswap.modules.blog.event;

import java.util.UUID;

/** Signals that a committed mutation can change blog trending eligibility or ranking. */
public record BlogTrendingRankingChangedEvent(UUID postId) {
}
