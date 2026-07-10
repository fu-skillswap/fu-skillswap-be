package com.fptu.exe.skillswap.modules.filestorage.service;

import com.fptu.exe.skillswap.modules.filestorage.dto.FileStorageProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

@Configuration
@RequiredArgsConstructor
public class R2Config {

    private final FileStorageProperties properties;

    @Bean
    public S3Presigner s3Presigner() {
        if (properties.getAccessKey() == null || properties.getAccessKey().isBlank()) {
            // Return a dummy presigner or null if not configured, or let it throw.
            // For safety when testing without R2 configured, we can just return a default one
            // but it will fail if actually used.
            return S3Presigner.builder()
                    .region(Region.US_EAST_1)
                    .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("dummy", "dummy")))
                    .build();
        }
        
        return S3Presigner.builder()
                .endpointOverride(URI.create(properties.getEndpoint()))
                .region(Region.US_EAST_1) // Cloudflare R2 uses auto or us-east-1 for AWS SDK compatibility
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(properties.getAccessKey(), properties.getSecretKey())))
                .build();
    }
}
