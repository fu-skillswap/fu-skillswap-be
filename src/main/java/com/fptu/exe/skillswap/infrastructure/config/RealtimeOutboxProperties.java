package com.fptu.exe.skillswap.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@ConfigurationProperties(prefix = "application.realtime.outbox")
@Validated
public class RealtimeOutboxProperties {

    private boolean enabled = false;
    private boolean cleanupEnabled = true;
    @Min(1)
    private int retentionDays = 7;
    @Min(10)
    private int cleanupBatchSize = 500;
    @Min(1)
    private int maxRetryAttempts = 3;
    @Min(1)
    private int maxBatchesPerRun = 100;
    @Min(10)
    private int maxExecutionSeconds = 300;
    @Min(1)
    private int batchTransactionTimeoutSeconds = 10;
    @NotBlank
    private String cleanupCron = "0 0 2 * * *";
    @Min(1000)
    private long pollMs = 30000L;
    @NotBlank
    private String exchange = "skillswap.domain-events";
    @NotBlank
    private String chatQueue = "skillswap.chat.realtime";
    @NotBlank
    private String notificationQueue = "skillswap.notification.realtime";
    @NotBlank
    private String deadLetterExchange = "skillswap.domain-events.dlx";
    @NotBlank
    private String chatDlqQueue = "skillswap.chat.realtime.dlq";
    @NotBlank
    private String notificationDlqQueue = "skillswap.notification.realtime.dlq";
    @NotBlank
    private String chatDlqRoutingKey = "chat.dlq";
    @NotBlank
    private String notificationDlqRoutingKey = "notification.dlq";
    @NotBlank
    private String chatRoutingPattern = "chat.#";
    @NotBlank
    private String notificationRoutingPattern = "notification.#";
}
