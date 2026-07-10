package com.fptu.exe.skillswap.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "application.google")
@Getter
@Setter
public class GoogleApiProperties {
    private String clientId;
    private String clientSecret;
    private String calendarRedirectUri;
    private String tokenEncryptionKey;
    private Integer tokenEncryptionKeyVersion = 1;
}
