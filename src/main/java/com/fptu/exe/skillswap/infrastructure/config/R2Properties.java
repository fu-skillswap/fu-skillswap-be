package com.fptu.exe.skillswap.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@ConfigurationProperties(prefix = "application.r2")
@Validated
public class R2Properties {

    private boolean enabled;

    private String endpoint;

    private String accessKeyId;

    private String secretAccessKey;

    private String bucket;

    @NotBlank(message = "application.r2.region không được để trống")
    private String region = "auto";

    @NotBlank(message = "application.r2.documents-prefix không được để trống")
    private String documentsPrefix = "skillswap/verification-documents";
}
