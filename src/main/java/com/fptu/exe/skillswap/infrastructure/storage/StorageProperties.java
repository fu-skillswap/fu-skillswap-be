package com.fptu.exe.skillswap.infrastructure.storage;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@Getter
@Setter
@ConfigurationProperties(prefix = "application.storage")
public class StorageProperties {

    /**
     * Active provider for new course videos. Bunny remains supported for
     * existing materials, but R2 is the default for new deployments.
     */
    private String videoProvider = "R2";

    // Mặc định tắt, chỉ bật khi .env có STORAGE_ENABLED=true.
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

    /** Provider-neutral prefix reserved for future course video objects. */
    private String videoPrefix = "course-materials/videos";

    /** Maximum size for one uploaded course video in MiB. */
    private int maxVideoSizeMb = 500;

    /** Keep the MVP upload policy narrow so playback compatibility is predictable. */
    private List<String> allowedVideoContentTypes = List.of("video/mp4");
}
