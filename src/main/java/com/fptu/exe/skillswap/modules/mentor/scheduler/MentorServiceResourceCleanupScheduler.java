package com.fptu.exe.skillswap.modules.mentor.scheduler;

import com.fptu.exe.skillswap.infrastructure.storage.StorageGateway;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorServiceResourceUploadIntent;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorServiceResourceUploadIntentRepository;
import com.fptu.exe.skillswap.shared.util.DateTimeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Component @RequiredArgsConstructor @Slf4j
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(prefix = "application.scheduling", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MentorServiceResourceCleanupScheduler {
    private static final AtomicBoolean RUNNING = new AtomicBoolean();
    private final MentorServiceResourceUploadIntentRepository repository;
    private final ObjectProvider<StorageGateway> storageGatewayProvider;
    private final TransactionTemplate transactionTemplate;

    @Scheduled(fixedDelayString = "${application.mentor-resources.cleanup-delay-ms:900000}")
    public void cleanup() {
        if (storageGatewayProvider.getIfAvailable() == null) return;
        if (!RUNNING.compareAndSet(false, true)) return;
        try {
            List<Claim> claims = transactionTemplate.execute(status -> claim());
            if (claims != null) for (Claim claim : claims) delete(claim);
        } finally { RUNNING.set(false); }
    }

    private List<Claim> claim() {
        LocalDateTime now = DateTimeUtil.now();
        List<MentorServiceResourceUploadIntent> intents = repository.claimCleanupBatch(now, 100);
        for (MentorServiceResourceUploadIntent intent : intents) {
            if (intent.getStatus() == MentorServiceResourceUploadIntent.Status.PENDING_UPLOAD) {
                intent.setStatus(MentorServiceResourceUploadIntent.Status.EXPIRED);
            }
            intent.setCleanupLeaseUntil(now.plusMinutes(5));
            intent.setCleanupAttemptCount(intent.getCleanupAttemptCount() + 1);
        }
        return intents.stream().map(intent -> new Claim(intent.getId(), intent.getStorageKey())).toList();
    }
    private void delete(Claim claim) {
        try {
            StorageGateway storageGateway = storageGatewayProvider.getIfAvailable();
            if (storageGateway == null) return;
            storageGateway.deleteFile(claim.objectKey());
            transactionTemplate.executeWithoutResult(status -> repository.findByIdForUpdate(claim.id()).ifPresent(intent -> {
                intent.setStorageDeletedAt(DateTimeUtil.now()); intent.setCleanupLeaseUntil(null); intent.setLastCleanupError(null);
            }));
        } catch (RuntimeException ex) {
            transactionTemplate.executeWithoutResult(status -> repository.findByIdForUpdate(claim.id()).ifPresent(intent -> {
                intent.setCleanupLeaseUntil(null); intent.setLastCleanupError(ex.getMessage());
                intent.setNextCleanupAt(DateTimeUtil.now().plusMinutes(Math.min(60, 1 << Math.min(6, intent.getCleanupAttemptCount()))));
            }));
            log.warn("Could not remove expired mentor resource object {}", claim.id(), ex);
        }
    }
    private record Claim(java.util.UUID id, String objectKey) {}
}
