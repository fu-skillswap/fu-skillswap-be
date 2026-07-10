package com.fptu.exe.skillswap.infrastructure.storage;

import lombok.Getter;
import lombok.Setter;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@ConfigurationProperties(prefix = "application.storage")
@Validated
public class StorageProperties {

    private boolean enabled = true;

    @NotBlank(message = "application.storage.endpoint không được để trống")
    private String endpoint;

    @NotBlank(message = "application.storage.access-key không được để trống")
    private String accessKey;

    @NotBlank(message = "application.storage.secret-key không được để trống")
    private String secretKey;

    @NotBlank(message = "application.storage.bucket không được để trống")
    private String bucket;

    @NotBlank(message = "application.storage.region không được để trống")
    private String region = "auto";

    @NotBlank(message = "application.storage.public-url-prefix không được để trống")
    private String publicUrlPrefix;

    private String documentsPrefix = "skillswap/verification-documents";
}
