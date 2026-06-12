package com.fptu.exe.skillswap.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "application.r2")
public class R2Properties {

    private boolean enabled;

    private String endpoint;

    private String accessKeyId;

    private String secretAccessKey;

    private String bucket;

    private String region = "auto";

    private String documentsPrefix = "skillswap/verification-documents";
}
