package com.fptu.exe.skillswap.infrastructure.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.net.URI;

@Configuration
@EnableConfigurationProperties(R2Properties.class)
public class R2StorageConfig {

    @Bean
    @ConditionalOnProperty(prefix = "application.r2", name = "enabled", havingValue = "true")
    public S3Client r2S3Client(R2Properties properties) {
        validate(properties);
        return S3Client.builder()
                .endpointOverride(URI.create(properties.getEndpoint()))
                .region(Region.of(properties.getRegion()))
                .credentialsProvider(
                        StaticCredentialsProvider.create(
                                AwsBasicCredentials.create(properties.getAccessKeyId(), properties.getSecretAccessKey())
                        )
                )
                .forcePathStyle(true)
                .build();
    }

    private void validate(R2Properties properties) {
        if (!StringUtils.hasText(properties.getEndpoint())
                || !StringUtils.hasText(properties.getAccessKeyId())
                || !StringUtils.hasText(properties.getSecretAccessKey())
                || !StringUtils.hasText(properties.getBucket())) {
            throw new BaseException(
                    ErrorCode.CONFIGURATION_ERROR,
                    "R2 được bật nhưng thiếu R2_ENDPOINT, R2_ACCESS_KEY_ID, R2_SECRET_ACCESS_KEY hoặc R2_BUCKET"
            );
        }
    }
}
