package com.fptu.exe.skillswap.infrastructure.config;

import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

/** Configuration shared by the API-issued playback grant and the VPS route. */
@Configuration
@ConfigurationProperties(prefix = "application.video-playback")
@Validated
@Getter
@Setter
public class VideoPlaybackProperties {

    /** Empty means the browser uses the current origin, which is the normal VPS setup. */
    private String baseUrl = "";

    private String streamPath = "/stream/videos";

    @Min(30)
    private long tokenTtlSeconds = 300;

    /** Override with a dedicated secret in production; the JWT secret is a local fallback only. */
    private String signingSecret;
}
