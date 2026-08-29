package com.fptu.exe.skillswap.infrastructure.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "application.course-material")
public class CourseMaterialProperties {
    @Min(1)
    @Max(120)
    private int pdfUploadTtlMinutes = 15;
    @Min(1)
    @Max(60)
    private int pdfDownloadTtlMinutes = 10;
    @Min(1)
    @Max(100)
    private int maxPdfSizeMb = 25;
}
