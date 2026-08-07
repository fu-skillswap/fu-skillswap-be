package com.fptu.exe.skillswap.infrastructure.storage;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "application.storage.lifecycle")
public class StorageLifecycleProperties {

    /**
     * Có bật tính năng dọn dẹp data hết hạn không
     */
    private boolean cleanupEnabled = true;

    /**
     * Có bật tính năng archive DB lên R2 không
     */
    private boolean archiveEnabled = true;

    /**
     * Thời gian lưu trữ email outbox (ngày)
     */
    private int emailOutboxRetentionDays = 7;

    /**
     * Thời gian lưu trữ course outbox (ngày)
     */
    private int courseOutboxRetentionDays = 7;

    /**
     * Thời gian lưu trữ bunny webhook events (ngày)
     */
    private int bunnyWebhookRetentionDays = 7;

    /**
     * Thời gian lưu trữ telemetry trên DB trước khi đẩy lên R2 (ngày)
     */
    private int internalTelemetryRetentionDays = 14;

    /**
     * Thời gian lưu trữ audit log trên DB trước khi đẩy lên R2 (ngày)
     */
    private int auditLogRetentionDays = 90;
}
