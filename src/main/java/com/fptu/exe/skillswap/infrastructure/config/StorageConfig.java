package com.fptu.exe.skillswap.infrastructure.config;

import com.fptu.exe.skillswap.infrastructure.storage.StorageProperties;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

@Configuration
@EnableConfigurationProperties(StorageProperties.class)
public class StorageConfig {

    @Bean
    @ConditionalOnProperty(prefix = "application.storage", name = "enabled", havingValue = "true")
    public S3Client storageS3Client(StorageProperties properties) {
        validate(properties);
        return S3Client.builder()
                .endpointOverride(URI.create(properties.getEndpoint()))
                .region(Region.of(properties.getRegion()))
                .credentialsProvider(
                        StaticCredentialsProvider.create(
                                AwsBasicCredentials.create(properties.getAccessKey(), properties.getSecretKey())
                        )
                )
                .forcePathStyle(true)
                .build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "application.storage", name = "enabled", havingValue = "true")
    public S3Presigner storageS3Presigner(StorageProperties properties) {
        validate(properties);
        return S3Presigner.builder()
                .endpointOverride(URI.create(properties.getEndpoint()))
                .region(Region.of(properties.getRegion()))
                .credentialsProvider(
                        StaticCredentialsProvider.create(
                                AwsBasicCredentials.create(properties.getAccessKey(), properties.getSecretKey())
                        )
                )
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();
    }

    private void validate(StorageProperties properties) {
        if (!StringUtils.hasText(properties.getEndpoint())
                || !StringUtils.hasText(properties.getAccessKey())
                || !StringUtils.hasText(properties.getSecretKey())
                || !StringUtils.hasText(properties.getBucket())) {
            throw new BaseException(
                    ErrorCode.CONFIGURATION_ERROR,
                    "Storage Gateway chưa cấu hình đầy đủ. Hãy kiểm tra STORAGE_ENDPOINT, STORAGE_ACCESS_KEY, STORAGE_SECRET_KEY và STORAGE_BUCKET"
            );
        }
    }
}
