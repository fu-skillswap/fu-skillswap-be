package com.fptu.exe.skillswap.modules.notification.scheduler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fptu.exe.skillswap.infrastructure.storage.StorageLifecycleProperties;
import com.fptu.exe.skillswap.infrastructure.storage.archive.StorageArchiveService;
import com.fptu.exe.skillswap.modules.notification.domain.Notification;
import com.fptu.exe.skillswap.modules.notification.domain.NotificationArchiveManifest;
import com.fptu.exe.skillswap.modules.notification.domain.NotificationRepository;
import com.fptu.exe.skillswap.modules.notification.service.NotificationArchivePersistenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(prefix = "application.storage.lifecycle", name = "cleanup-enabled", havingValue = "true")
public class NotificationRetentionScheduler {

    private final NotificationRepository notificationRepository;
    private final NotificationArchivePersistenceService persistenceService;
    private final StorageArchiveService archiveService;
    private final StorageLifecycleProperties properties;
    private final ObjectMapper objectMapper;

    @Scheduled(cron = "${application.storage.lifecycle.notification-retention-cron:0 20 2 * * *}", zone = "Asia/Ho_Chi_Minh")
    public void archiveReadAndDeleteExpiredNotifications() {
        if (!properties.isArchiveEnabled()) {
            return;
        }

        LocalDateTime now = com.fptu.exe.skillswap.shared.util.DateTimeUtil.now();
        LocalDateTime readCutoff = now.minusDays(properties.getNotificationReadArchiveDays());
        int batchSize = Math.max(50, Math.min(500, properties.getCleanupBatchSize()));
        int totalArchived = 0;

        List<UUID> userIds = notificationRepository.findUsersWithArchivableNotifications(
                readCutoff, PageRequest.of(0, 100));
        for (UUID userId : userIds) {
            List<Notification> notifications = notificationRepository
                    .findTop500ByRecipientUserIdAndReadAtNotNullAndReadAtBeforeOrderByReadAtAscIdAsc(userId, readCutoff);
            for (int offset = 0; offset < notifications.size(); offset += batchSize) {
                List<Notification> batch = notifications.subList(offset, Math.min(offset + batchSize, notifications.size()));
                totalArchived += archiveBatch(userId, batch, readCutoff);
            }
        }

        log.info("Notification retention archive completed. archived={}, users={}", totalArchived, userIds.size());
    }

    @Scheduled(cron = "${application.storage.lifecycle.notification-unread-cleanup-cron:0 35 2 * * *}", zone = "Asia/Ho_Chi_Minh")
    @Transactional
    public void deleteExpiredUnreadNotifications() {
        int retentionDays = properties.getNotificationUnreadRetentionDays();
        if (retentionDays <= 0) {
            return;
        }
        LocalDateTime cutoff = com.fptu.exe.skillswap.shared.util.DateTimeUtil.now().minusDays(retentionDays);
        int totalDeleted = 0;
        int batchSize = Math.max(50, Math.min(500, properties.getCleanupBatchSize()));
        for (int batch = 0; batch < 100; batch++) {
            int deleted = notificationRepository.deleteUnreadBefore(cutoff, batchSize);
            totalDeleted += deleted;
            if (deleted < batchSize) {
                break;
            }
        }
        log.info("Expired unread notification cleanup completed. deleted={}, retentionDays={}", totalDeleted, retentionDays);
    }

    private int archiveBatch(UUID userId, List<Notification> batch, LocalDateTime cutoff) {
        List<String> jsonLines = new ArrayList<>(batch.size());
        for (Notification notification : batch) {
            try {
                jsonLines.add(objectMapper.writeValueAsString(toArchiveMap(notification, userId)));
            } catch (JsonProcessingException ex) {
                throw new IllegalStateException("Cannot serialize notification archive batch", ex);
            }
        }

        LocalDateTime periodStart = batch.stream().map(Notification::getCreatedAt).min(LocalDateTime::compareTo).orElseThrow();
        LocalDateTime periodEnd = batch.stream().map(Notification::getCreatedAt).max(LocalDateTime::compareTo).orElseThrow();
        String identity = "user-" + userId + "-" + periodStart.toLocalDate();
        StorageArchiveService.ArchiveBatchResult archive = archiveService.archiveJsonLines(
                "archives/notifications/" + periodStart.toLocalDate().getYear(), identity, jsonLines);
        if (archive == null) {
            return 0;
        }

        NotificationArchiveManifest manifest = NotificationArchiveManifest.builder()
                .userId(userId)
                .periodStart(periodStart)
                .periodEnd(periodEnd)
                .storageKey(archive.objectKey())
                .checksum(archive.sha256Hex())
                .recordCount(archive.rowCount())
                .build();

        return persistenceService.saveManifestAndDelete(
                manifest,
                batch.stream().map(Notification::getId).toList(),
                cutoff);
    }

    private LinkedHashMap<String, Object> toArchiveMap(Notification notification, UUID userId) {
        LinkedHashMap<String, Object> value = new LinkedHashMap<>();
        value.put("id", notification.getId());
        value.put("userId", userId);
        value.put("type", notification.getType());
        value.put("title", notification.getTitle());
        value.put("message", notification.getMessage());
        value.put("relatedEntityType", notification.getRelatedEntityType());
        value.put("relatedEntityId", notification.getRelatedEntityId());
        value.put("deepLink", notification.getDeepLink());
        value.put("readAt", notification.getReadAt());
        value.put("createdAt", notification.getCreatedAt());
        return value;
    }
}
