package com.fptu.exe.skillswap.shared.ratelimit;

/**
 * Isolates rate-limit buckets so high-cardinality, best-effort traffic cannot
 * evict security or business-write buckets on the single application node.
 */
public enum RateLimitScope {
    SECURITY,
    BUSINESS,
    TRANSFER,
    BEST_EFFORT
}
