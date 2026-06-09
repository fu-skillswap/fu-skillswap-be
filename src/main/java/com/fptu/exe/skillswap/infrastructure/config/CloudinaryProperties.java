package com.fptu.exe.skillswap.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * CloudinaryProperties – Binding cấu hình Cloudinary từ application.yaml.
 *
 * <p>Prefix: {@code application.cloudinary}
 */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "application.cloudinary")
public class CloudinaryProperties {

    /** Cloudinary Cloud Name (bắt buộc). */
    private String cloudName;

    /** Cloudinary API Key (bắt buộc). */
    private String apiKey;

    /** Cloudinary API Secret (bắt buộc). */
    private String apiSecret;

    /**
     * Thư mục gốc lưu file trên Cloudinary.
     * Mặc định: {@code skillswap}
     */
    private String folder = "skillswap";
}
