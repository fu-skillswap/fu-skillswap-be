package com.fptu.exe.skillswap.modules.booking.scheduler;

import com.fptu.exe.skillswap.infrastructure.storage.StorageLifecycleProperties;
import com.fptu.exe.skillswap.modules.booking.service.BookingIssueEvidenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BookingIssueEvidenceCleanupScheduler {
    private final BookingIssueEvidenceService evidenceService;
    private final StorageLifecycleProperties storageLifecycleProperties;

    @Scheduled(cron = "${booking.dispute-evidence.cleanup-cron:0 10 3 * * *}")
    public void clean() {
        if (!storageLifecycleProperties.isCleanupEnabled()) return;
        evidenceService.cleanExpiredUploadIntents();
        evidenceService.cleanResolvedEvidence();
    }
}
