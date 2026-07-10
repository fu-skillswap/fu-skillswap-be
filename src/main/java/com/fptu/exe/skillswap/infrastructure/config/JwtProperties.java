package com.fptu.exe.skillswap.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

@Configuration
@ConfigurationProperties(prefix = "application.security")
@Validated
@Getter
@Setter
public class JwtProperties {

    @Valid
    private Jwt jwt = new Jwt();

    @Getter
    @Setter
    public static class Jwt {
        @NotBlank
        private String secretKey;
        @Min(1)
        private long expiration; // in milliseconds
        @Valid
        private RefreshToken refreshToken = new RefreshToken();

        @Getter
        @Setter
        public static class RefreshToken {
            @Min(1)
            private long expiration; // in milliseconds

            @Min(1000)
            private long rotationGracePeriodMillis = 30_000L;
        }
    }

}
