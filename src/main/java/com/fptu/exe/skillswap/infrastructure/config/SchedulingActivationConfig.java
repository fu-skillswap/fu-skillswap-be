package com.fptu.exe.skillswap.infrastructure.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Vẫn tạo executor cho worker nhưng cho phép chạy kiểm tra migration
 * mà không kích hoạt các job nền ghi dữ liệu.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "application.scheduling", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingActivationConfig {
}
