package com.fptu.exe.skillswap.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "application.availability.templates")
public record AvailabilityTemplateProperties(
        String generationMode,
        int horizonDays,
        int schedulerBatchSize,
        int schedulerMaxTemplatesPerRun,
        int claimLeaseSeconds
) {
    public AvailabilityTemplateProperties {
        generationMode = generationMode == null ? "TEMPLATES" : generationMode;
        horizonDays = horizonDays <= 0 ? 28 : horizonDays;
        schedulerBatchSize = schedulerBatchSize <= 0 ? 50 : schedulerBatchSize;
        schedulerMaxTemplatesPerRun = schedulerMaxTemplatesPerRun <= 0 ? 1000 : schedulerMaxTemplatesPerRun;
        claimLeaseSeconds = claimLeaseSeconds <= 0 ? 120 : claimLeaseSeconds;
    }

    public boolean templatesEnabled() {
        return "TEMPLATES".equalsIgnoreCase(generationMode);
    }
}
