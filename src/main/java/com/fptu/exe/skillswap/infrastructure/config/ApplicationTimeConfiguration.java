package com.fptu.exe.skillswap.infrastructure.config;

import com.fptu.exe.skillswap.shared.util.DateTimeUtil;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/** Provides one injectable UTC clock for production code and deterministic tests. */
@Configuration
public class ApplicationTimeConfiguration {

    @Bean
    Clock applicationClock() {
        Clock clock = Clock.systemUTC();
        // Legacy entity callbacks still use the static facade. Keeping the facade
        // backed by the same injected Clock prevents two different notions of now.
        DateTimeUtil.setClock(clock);
        return clock;
    }
}
