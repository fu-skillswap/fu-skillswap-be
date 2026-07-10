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
@ConfigurationProperties(prefix = "application.websocket.stomp")
@Validated
@Getter
@Setter
public class StompRelayProperties {

    private boolean enabled = false;
    @NotBlank
    private String endpoint = "/ws-stomp";
    @NotBlank
    private String appDestinationPrefix = "/app";
    @NotBlank
    private String userDestinationPrefix = "/user";
    private boolean autoStartup = true;
    private String clientLogin = "guest";
    private String clientPasscode = "guest";
    private String systemLogin = "guest";
    private String systemPasscode = "guest";
    @Min(0)
    private long systemHeartbeatSendIntervalMs = 10_000L;
    @Min(0)
    private long systemHeartbeatReceiveIntervalMs = 10_000L;
    @Min(1024)
    private int messageSizeLimit = 128 * 1024;
    @Min(1024)
    private int sendBufferSizeLimit = 512 * 1024;
    @Min(1000)
    private int sendTimeLimitMs = 15_000;
    @Valid
    private Relay relay = new Relay();

    @Getter
    @Setter
    public static class Relay {
        @NotBlank
        private String host = "localhost";
        @Min(1)
        private int port = 61613;
        @NotBlank
        private String username = "guest";
        @NotBlank
        private String password = "guest";
    }
}
