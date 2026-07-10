package com.fptu.exe.skillswap.shared.idempotency;

import com.fptu.exe.skillswap.shared.util.DateTimeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class IdempotencyCleanupScheduler {

    private final IdempotencyKeyRepository idempotencyKeyRepository;

    @Scheduled(cron = "0 0 2 * * *") // Chạy vào lúc 2:00 AM mỗi ngày
    @Transactional
    public void cleanupOldKeys() {
        log.info("[{}] Bắt đầu dọn dẹp các Idempotency Key quá hạn (lớn hơn 24h)...", Thread.currentThread().getName());
        LocalDateTime threshold = DateTimeUtil.now().minusHours(24);
        int deletedCount = idempotencyKeyRepository.deleteByCreatedAtBefore(threshold);
        log.info("[{}] Đã xóa thành công {} bản ghi Idempotency Key cũ.", Thread.currentThread().getName(), deletedCount);
    }
}
