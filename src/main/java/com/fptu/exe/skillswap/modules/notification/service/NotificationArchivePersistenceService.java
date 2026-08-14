package com.fptu.exe.skillswap.modules.notification.service;

import com.fptu.exe.skillswap.modules.notification.domain.NotificationArchiveManifest;
import com.fptu.exe.skillswap.modules.notification.domain.NotificationArchiveManifestRepository;
import com.fptu.exe.skillswap.modules.notification.domain.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationArchivePersistenceService {

    private final NotificationRepository notificationRepository;
    private final NotificationArchiveManifestRepository manifestRepository;

    @Transactional
    public int saveManifestAndDelete(NotificationArchiveManifest manifest, List<UUID> notificationIds,
                                     LocalDateTime cutoff) {
        manifestRepository.save(manifest);
        int deleted = notificationRepository.deleteArchivedBatch(notificationIds, cutoff);
        if (deleted != notificationIds.size()) {
            throw new IllegalStateException("Notification archive delete count does not match the archived batch");
        }
        return deleted;
    }
}
