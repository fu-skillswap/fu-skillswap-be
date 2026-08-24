package com.fptu.exe.skillswap.infrastructure.config;

import com.fptu.exe.skillswap.shared.time.DefaultTimeProvider;
import com.fptu.exe.skillswap.shared.time.TimeProvider;
import com.fptu.exe.skillswap.shared.util.DateTimeUtil;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.time.Clock;

/** Provides injectable UTC clock and TimeProvider for production code and deterministic tests. */
@Configuration
public class ApplicationTimeConfiguration {

    @Bean
    @Primary
    @ConditionalOnMissingBean(Clock.class)
    public Clock applicationClock() {
        Clock clock = Clock.systemUTC();
        // Legacy entity callbacks still use the static facade. Keeping the facade
        // backed by the same injected Clock prevents two different notions of now.
        DateTimeUtil.setClock(clock);
        return clock;
    }

    @Bean
    @Primary
    @ConditionalOnMissingBean(TimeProvider.class)
    public TimeProvider timeProvider(Clock clock) {
        // Entity callbacks that have not yet been contracted to UTC still use
        // the legacy facade. Bind it to the resolved Clock as well, including
        // a deterministic Clock supplied by an integration test.
        DateTimeUtil.setClock(clock);
        return new DefaultTimeProvider(clock);
    }
}
