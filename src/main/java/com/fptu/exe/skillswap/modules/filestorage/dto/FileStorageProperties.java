package com.fptu.exe.skillswap.modules.filestorage.dto;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "application.filestorage.r2")
@Getter
@Setter
public class FileStorageProperties {
    private String endpoint;
    private String accessKey;
    private String secretKey;
    private String bucketName;
    private String publicUrlPrefix;
}
