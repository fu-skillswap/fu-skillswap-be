package com.fptu.exe.skillswap.infrastructure.storage;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@Getter
@Setter
@ConfigurationProperties(prefix = "application.storage")
public class StorageProperties {

    // Default false — storage must be explicitly enabled via STORAGE_ENABLED=true in .env
    private boolean enabled = false;

    private String endpoint;

    private String accessKey;

    private String secretKey;

    private String bucket;

    private String region = "auto";

    private String publicUrlPrefix;

    private String documentsPrefix = "skillswap/verification-documents";

    private int presignedTtlMinutes = 15;

    private List<String> allowedContentTypes = List.of("image/jpeg", "image/png", "application/pdf");
}
