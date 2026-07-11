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
     * Danh sách các host (domain) được phép xuất hiện trong URL minh chứng.
     * Mặc định bao gồm CDN public của SkillSwap và local storage host.
     */
    @NotEmpty
    private List<String> allowedUrlHosts = List.of("cdn.skillswap.com", "storage.skillswap.local");
}
