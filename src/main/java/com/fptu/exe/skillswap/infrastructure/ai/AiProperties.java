package com.fptu.exe.skillswap.infrastructure.ai;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "application.ai")
public class AiProperties {

    private boolean enabled = true;
    private String baseUrl = "http://localhost:8000";
    private String internalToken = "skillswap-internal-ai-secret";
    private int connectTimeoutMs = 2000;
    private int readTimeoutMs = 3000;
    private UUID botUserId = UUID.fromString("019f0000-0000-7000-8000-0000000000a1");
}
