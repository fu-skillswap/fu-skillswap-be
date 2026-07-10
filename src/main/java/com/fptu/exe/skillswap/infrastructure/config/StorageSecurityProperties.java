package com.fptu.exe.skillswap.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "application.storage.security")
@Validated
public class StorageSecurityProperties {
    
    /**
     * Danh sách các host (domain) được phép upload file minh chứng.
     * Mặc định là res.cloudinary.com
     */
    @NotEmpty
    private List<String> allowedUrlHosts = List.of("res.cloudinary.com");
}
