package com.fptu.exe.skillswap.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "application.discovery")
public record DiscoveryProperties(
        int recallWindowSize,
        String recommendationAlgorithmVersion
) {
    public DiscoveryProperties {
        recallWindowSize = recallWindowSize <= 0 ? 100 : recallWindowSize;
        recommendationAlgorithmVersion = recommendationAlgorithmVersion == null
                || recommendationAlgorithmVersion.isBlank()
                ? "structured-v1"
                : recommendationAlgorithmVersion.trim();
    }
}
