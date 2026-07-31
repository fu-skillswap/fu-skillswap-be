package com.fptu.exe.skillswap.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "application.group-sessions")
public class GroupSessionProperties {
    /** Supply APIs stay off until Phase 2 commerce is ready for a learner-facing rollout. */
    private boolean enabled = false;

    /** Mentor must submit the immutable group roster within this bounded post-session window. */
    private int attendanceSubmissionWindowHours = 48;
}
