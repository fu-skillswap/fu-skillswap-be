package com.fptu.exe.skillswap.shared.ratelimit;

/**
 * Tách bucket rate limit để traffic nhiều key, không quan trọng không đẩy bucket
 * security hoặc business-write ra khỏi cache của application node.
 */
public enum RateLimitScope {
    SECURITY,
    BUSINESS,
    TRANSFER,
    BEST_EFFORT
}
