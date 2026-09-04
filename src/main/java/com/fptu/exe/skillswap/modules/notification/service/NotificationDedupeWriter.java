package com.fptu.exe.skillswap.modules.notification.service;

import com.fptu.exe.skillswap.modules.notification.domain.Notification;
import com.fptu.exe.skillswap.modules.notification.domain.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
class NotificationDedupeWriter {

    private final NotificationRepository notificationRepository;

    /**
     * Flush the unique dedupe key in an independent transaction. If another
     * consumer wins the race, the caller can safely catch the constraint
     * violation without marking its surrounding business transaction rollback-only.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    Notification persist(Notification notification) {
        return notificationRepository.saveAndFlush(notification);
    }
}
