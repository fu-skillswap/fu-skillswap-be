package com.fptu.exe.skillswap.infrastructure.config;

import com.cloudinary.Cloudinary;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.util.Map;

/**
 * CloudinaryConfig – Khởi tạo Cloudinary Bean dùng chung toàn ứng dụng.
 */
@Configuration
@RequiredArgsConstructor
public class CloudinaryConfig {

    private final CloudinaryProperties cloudinaryProperties;

    @Bean
    @ConditionalOnProperty(prefix = "application.cloudinary", name = "enabled", havingValue = "true")
    public Cloudinary cloudinary() {
        validateCloudinaryProperties();
        return new Cloudinary(Map.of(
                "cloud_name", cloudinaryProperties.getCloudName(),
                "api_key",    cloudinaryProperties.getApiKey(),
                "api_secret", cloudinaryProperties.getApiSecret(),
                "secure",     true   // luôn dùng HTTPS
        ));
    }

    private void validateCloudinaryProperties() {
        if (!StringUtils.hasText(cloudinaryProperties.getCloudName())
                || !StringUtils.hasText(cloudinaryProperties.getApiKey())
                || !StringUtils.hasText(cloudinaryProperties.getApiSecret())) {
            throw new BaseException(
                    ErrorCode.CONFIGURATION_ERROR,
                    "Cloudinary được bật nhưng thiếu CLOUDINARY_CLOUD_NAME, CLOUDINARY_API_KEY hoặc CLOUDINARY_API_SECRET"
            );
        }
    }
}
