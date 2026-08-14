package com.fptu.exe.skillswap.modules.chat.scheduler;

import com.fptu.exe.skillswap.infrastructure.storage.StorageGateway;
import com.fptu.exe.skillswap.infrastructure.storage.StorageLifecycleProperties;
import com.fptu.exe.skillswap.modules.chat.domain.ChatAttachment;
import com.fptu.exe.skillswap.modules.chat.domain.ChatAttachmentState;
import com.fptu.exe.skillswap.modules.chat.repository.ChatAttachmentRepository;
import com.fptu.exe.skillswap.modules.chat.service.ChatAttachmentCleanupPersistenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(prefix = "application.storage.lifecycle", name = "cleanup-enabled", havingValue = "true")
public class ChatAttachmentCleanupScheduler {

    private final ChatAttachmentRepository attachmentRepository;
    private final ObjectProvider<StorageGateway> storageGatewayProvider;
    private final StorageLifecycleProperties properties;
    private final ChatAttachmentCleanupPersistenceService persistenceService;

    @Scheduled(cron = "${application.storage.lifecycle.chat-attachment-cleanup-cron:0 50 2 * * *}")
    @Transactional
    public void markExpiredAttachments() {
        LocalDateTime now = LocalDateTime.now();
        List<ChatAttachment> candidates = attachmentRepository.findExpiredForTransition(
                ChatAttachmentState.ACTIVE, now, PageRequest.of(0, Math.max(50, properties.getCleanupBatchSize())));
        candidates.forEach(attachment -> attachment.setState(ChatAttachmentState.EXPIRED));
        if (!candidates.isEmpty()) {
            attachmentRepository.saveAll(candidates);
        }
        log.info("Chat attachment expiry transition completed. expired={}", candidates.size());
    }

    @Scheduled(cron = "${application.storage.lifecycle.chat-attachment-delete-cron:0 55 2 * * *}")
    public void deleteExpiredObjects() {
        StorageGateway storageGateway = storageGatewayProvider.getIfAvailable();
        if (storageGateway == null) {
            log.warn("Chat attachment cleanup skipped because storage gateway is unavailable");
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime cutoff = now.minusDays(properties.getChatAttachmentDeleteGraceDays());
        List<ChatAttachment> candidates = attachmentRepository.findReadyForPhysicalDeletion(
                EnumSet.of(ChatAttachmentState.EXPIRED, ChatAttachmentState.REVOKED, ChatAttachmentState.TAKEN_DOWN),
                cutoff,
                now,
                PageRequest.of(0, Math.max(50, properties.getCleanupBatchSize())));

        int deleted = 0;
        for (ChatAttachment attachment : candidates) {
            try {
                storageGateway.deleteFile(attachment.getStorageKey());
                deleted += persistenceService.markDeletedIfStillEligible(attachment.getId(), cutoff, now);
            } catch (RuntimeException ex) {
                log.error("Chat attachment object deletion failed. attachmentId={}, key={}",
                        attachment.getId(), attachment.getStorageKey(), ex);
            }
        }
        log.info("Chat attachment physical cleanup completed. candidates={}, deleted={}", candidates.size(), deleted);
    }

}
