package com.fptu.exe.skillswap.infrastructure.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Keeps worker executors available while allowing isolated migration rehearsals
 * to boot a candidate image without executing durable scheduled jobs.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "application.scheduling", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingActivationConfig {
}
