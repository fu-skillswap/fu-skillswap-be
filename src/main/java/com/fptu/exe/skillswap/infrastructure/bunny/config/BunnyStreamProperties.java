package com.fptu.exe.skillswap.infrastructure.bunny.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "application.bunny.stream")
@Getter
@Setter
public class BunnyStreamProperties {
    private String apiKey;
    private String libraryId;
    private String tokenAuthKey;
    private String cdnHostname = "iframe.mediadelivery.net";
    private String apiUrl = "https://video.bunnycdn.com";
    private String webhookSecret;
}
