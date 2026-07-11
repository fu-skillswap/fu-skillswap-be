package com.fptu.exe.skillswap.modules.identity.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.fptu.exe.skillswap.shared.event.UserBannedEvent;
import com.fptu.exe.skillswap.shared.event.UserDeletedEvent;
import com.fptu.exe.skillswap.modules.identity.event.UserStatusChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserBanStatusCacheInvalidationListener {

    private final Cache<UUID, Boolean> userBanStatusCache;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUserStatusChanged(UserStatusChangedEvent event) {
        invalidate(event.userId(), "UserStatusChangedEvent");
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUserBanned(UserBannedEvent event) {
        invalidate(event.getUserId(), "UserBannedEvent");
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUserDeleted(UserDeletedEvent event) {
        invalidate(event.getUserId(), "UserDeletedEvent");
    }

    private void invalidate(UUID userId, String source) {
        if (userId == null) {
            return;
        }
        userBanStatusCache.invalidate(userId);
        log.debug("Invalidated ban-status cache for user {} from {}", userId, source);
    }
}
