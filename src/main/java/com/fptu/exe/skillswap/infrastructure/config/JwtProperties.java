package com.fptu.exe.skillswap.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "application.security")
@Getter
@Setter
public class JwtProperties {

    private Jwt jwt = new Jwt();
    private Google google = new Google();

    @Getter
    @Setter
    public static class Jwt {
        private String secretKey;
        private long expiration; // in milliseconds
        private RefreshToken refreshToken = new RefreshToken();

        @Getter
        @Setter
        public static class RefreshToken {
            private long expiration; // in milliseconds
        }
    }

    @Getter
    @Setter
    public static class Google {
        private String clientId;
    }
}
