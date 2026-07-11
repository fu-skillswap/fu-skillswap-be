package com.fptu.exe.skillswap.modules.forum.event;

import com.fptu.exe.skillswap.modules.forum.repository.ForumCommentRepository;
import com.fptu.exe.skillswap.modules.forum.repository.ForumPostReactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class ForumDataCascadeListener {

    private final ForumCommentRepository forumCommentRepository;
    private final ForumPostReactionRepository forumPostReactionRepository;

    @Async("forumTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleForumPostDeleted(ForumPostDeletedEvent event) {
        String threadName = Thread.currentThread().getName();
        log.info("[{}] Bắt đầu xử lý xóa cascade cho bài viết (soft delete) {}", threadName, event.postId());
        try {
            forumCommentRepository.softDeleteByPostId(event.postId());
            forumPostReactionRepository.deleteByPostId(event.postId());
            log.info("[{}] Xử lý xóa cascade thành công cho bài viết {}", threadName, event.postId());
        } catch (Exception e) {
            log.error("[{}] Lỗi khi xử lý cascade delete cho bài viết {}: {}", threadName, event.postId(), e.getMessage());
        }
    }
}
