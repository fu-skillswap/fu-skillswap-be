package com.fptu.exe.skillswap.infrastructure.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/** Cấu hình chung cho các cache Caffeine trong một tiến trình. */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "application.cache")
public class CacheProperties {

    @Valid private TimedCache catalog = new TimedCache(1_000, Duration.ofHours(24));
    @Valid private TimedCache userBanStatus = new TimedCache(10_000, Duration.ofMinutes(10));
    @Valid private TimedCache googleOauthState = new TimedCache(10_000, Duration.ofMinutes(5));
    @Valid private TimedCache matchingFeatures = new TimedCache(20_000, Duration.ofMinutes(10));
    @Valid private TimedCache mentorFunnelDedupe = new TimedCache(20_000, Duration.ofMinutes(10));
    @Valid private TimedCache localPrivateDownloadCredential = new TimedCache(10_000, Duration.ofMinutes(10));
    @Valid private RateLimitCache rateLimit = new RateLimitCache();
    @Valid private BlogCache blog = new BlogCache();
    @Valid private TimedCache forumProhibitedPhrase = new TimedCache(1, Duration.ofMinutes(30));

    @Getter
    @Setter
    public static class TimedCache {
        @Min(1) private long maximumSize;
        @NotNull private Duration ttl;

        public TimedCache() {
        }

        public TimedCache(long maximumSize, Duration ttl) {
            this.maximumSize = maximumSize;
            this.ttl = ttl;
        }
    }

    @Getter
    @Setter
    public static class SizedCache {
        @Min(1) private long maximumSize;

        public SizedCache() {
        }

        public SizedCache(long maximumSize) {
            this.maximumSize = maximumSize;
        }
    }

    @Getter
    @Setter
    public static class RateLimitCache {
        // Tách riêng bucket nhạy cảm. Đây là giới hạn số entry, không phải giới hạn bộ nhớ.
        @Valid private SizedCache security = new SizedCache(10_000);
        @Valid private SizedCache business = new SizedCache(10_000);
        @Valid private SizedCache transfer = new SizedCache(5_000);
        @Valid private SizedCache bestEffort = new SizedCache(2_000);
    }

    @Getter
    @Setter
    public static class BlogCache {
        @Valid private TimedCache trending = new TimedCache(3, Duration.ofMinutes(10));
        @Min(1) private long trendingInvalidationDebounceMaximumSize = 1;
        @NotNull private Duration trendingInvalidationDebounce = Duration.ofSeconds(45);
        @Valid private TimedCache viewDedupe = new TimedCache(100_000, Duration.ofMinutes(30));
    }
}
