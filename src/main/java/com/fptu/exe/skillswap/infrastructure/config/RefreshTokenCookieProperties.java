package com.fptu.exe.skillswap.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "application.security.jwt.refresh-token.cookie")
@Getter
@Setter
public class RefreshTokenCookieProperties {

    private String name = "skillswap_refresh_token";
    private String path = "/";
    private boolean secure = true;
    private String sameSite = "Lax";
}
