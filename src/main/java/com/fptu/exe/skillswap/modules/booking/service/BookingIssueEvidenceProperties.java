package com.fptu.exe.skillswap.modules.booking.service;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "booking.dispute-evidence")
public class BookingIssueEvidenceProperties {

    private int maxFilesPerAction = 5;
    private long maxFileSizeBytes = 10L * 1024 * 1024;
    private int uploadIntentTtlMinutes = 15;
    private int downloadUrlTtlMinutes = 10;
    private int retentionDays = 180;
    private int uploadIntentRateLimit = 12;
    private int uploadIntentRateLimitWindowMinutes = 15;
}
