package com.fptu.exe.skillswap.modules.blog.event;

import com.fptu.exe.skillswap.modules.blog.domain.BlogVisibility;
import com.fptu.exe.skillswap.modules.blog.repository.BlogCategoryFollowRepository;
import com.fptu.exe.skillswap.modules.blog.repository.BlogTagFollowRepository;
import com.fptu.exe.skillswap.modules.mentor.service.MentorContentAccessService;
import com.fptu.exe.skillswap.modules.notification.domain.NotificationType;
import com.fptu.exe.skillswap.modules.notification.service.NotificationService;
import com.fptu.exe.skillswap.shared.util.DateTimeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class BlogPostPublishedNotificationListener {

    private final BlogCategoryFollowRepository blogCategoryFollowRepository;
    private final BlogTagFollowRepository blogTagFollowRepository;
    private final NotificationService notificationService;
    private final MentorContentAccessService mentorContentAccessService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBlogPostPublished(BlogPostPublishedEvent event) {
        try {
            Set<UUID> recipientIds = followerIds(event);
            recipientIds.remove(event.authorUserId());
            for (UUID recipientId : recipientIds) {
                if (!canReceive(event.visibility(), recipientId)) {
                    continue;
                }
                try {
                    notificationService.createNotification(
                            recipientId,
                            NotificationType.BLOG_POST_PUBLISHED,
                            "Bài blog mới từ SkillSwap",
                            event.authorName() + " vừa đăng bài: " + event.title(),
                            "BLOG_POST",
                            event.postId(),
                            "/blog/posts/" + event.slug()
                    );
                } catch (RuntimeException ex) {
                    log.error(notificationErrorJson(event, recipientId, ex));
                }
            }
        } catch (RuntimeException ex) {
            log.error(notificationErrorJson(event, null, ex));
        }
    }

    private Set<UUID> followerIds(BlogPostPublishedEvent event) {
        Set<UUID> recipientIds = new LinkedHashSet<>();
        if (event.categoryIds() != null && !event.categoryIds().isEmpty()) {
            recipientIds.addAll(blogCategoryFollowRepository.findFollowerUserIdsByCategoryIds(event.categoryIds()));
        }
        if (event.tagIds() != null && !event.tagIds().isEmpty()) {
            recipientIds.addAll(blogTagFollowRepository.findFollowerUserIdsByTagIds(event.tagIds()));
        }
        return recipientIds;
    }

    private boolean canReceive(BlogVisibility visibility, UUID recipientId) {
        if (visibility == BlogVisibility.MENTOR_ONLY) {
            return mentorContentAccessService.canAccessMentorOnlyContent(recipientId);
        }
        return true;
    }

    private String notificationErrorJson(BlogPostPublishedEvent event, UUID recipientId, RuntimeException ex) {
        return """
                {"level":"ERROR","type":"BLOG_NOTIFICATION_FAILURE","module":"blog","eventType":"BLOG_POST_PUBLISHED","eventId":"%s","postId":"%s","recipientUserId":"%s","errorSummary":"%s","occurredAt":"%s"}
                """.formatted(
                event.eventId(),
                event.postId(),
                recipientId,
                sanitize(ex.getMessage()),
                DateTimeUtil.now()
        ).trim();
    }

    private String sanitize(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
