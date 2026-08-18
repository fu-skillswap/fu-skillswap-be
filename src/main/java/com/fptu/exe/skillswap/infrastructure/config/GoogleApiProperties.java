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

    private String tokenEndpoint = "https://oauth2.googleapis.com/token";
    private String revokeEndpoint = "https://oauth2.googleapis.com/revoke";
    private String userinfoEndpoint = "https://openidconnect.googleapis.com/v1/userinfo";
    private String calendarBaseUrl = "https://www.googleapis.com/calendar/v3/calendars";
}
