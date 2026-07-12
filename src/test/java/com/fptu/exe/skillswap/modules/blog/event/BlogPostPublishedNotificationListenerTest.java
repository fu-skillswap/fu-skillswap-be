package com.fptu.exe.skillswap.modules.blog.event;

import com.fptu.exe.skillswap.modules.blog.domain.BlogVisibility;
import com.fptu.exe.skillswap.modules.blog.repository.BlogCategoryFollowRepository;
import com.fptu.exe.skillswap.modules.blog.repository.BlogTagFollowRepository;
import com.fptu.exe.skillswap.modules.mentor.service.MentorContentAccessService;
import com.fptu.exe.skillswap.modules.notification.domain.NotificationType;
import com.fptu.exe.skillswap.modules.notification.service.NotificationService;
import com.fptu.exe.skillswap.shared.util.UuidUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BlogPostPublishedNotificationListenerTest {

    @Mock private BlogCategoryFollowRepository categoryFollowRepository;
    @Mock private BlogTagFollowRepository tagFollowRepository;
    @Mock private NotificationService notificationService;
    @Mock private MentorContentAccessService mentorContentAccessService;

    @Test
    void onBlogPostPublished_shouldDedupeFollowerAcrossCategoryAndTag() {
        UUID recipientId = UUID.fromString("018f3abf-0a22-7112-9748-6cf000c47b6e");
        UUID authorId = UUID.fromString("018f3abf-0a22-7113-9748-6cf000c47b6e");
        UUID postId = UUID.fromString("018f3abf-0a22-7114-9748-6cf000c47b6e");
        UUID categoryId = UUID.fromString("018f3abf-0a22-7115-9748-6cf000c47b6e");
        UUID tagId = UUID.fromString("018f3abf-0a22-7116-9748-6cf000c47b6e");
        when(categoryFollowRepository.findFollowerUserIdsByCategoryIds(Set.of(categoryId))).thenReturn(Set.of(recipientId));
        when(tagFollowRepository.findFollowerUserIdsByTagIds(Set.of(tagId))).thenReturn(Set.of(recipientId));
        BlogPostPublishedNotificationListener listener = new BlogPostPublishedNotificationListener(
                categoryFollowRepository,
                tagFollowRepository,
                notificationService,
                mentorContentAccessService
        );

        listener.onBlogPostPublished(new BlogPostPublishedEvent(
                UuidUtil.generateUuidV7(),
                postId,
                "spring-security-guide",
                "Spring Security Guide",
                authorId,
                "Nguyen A",
                BlogVisibility.PUBLIC,
                Set.of(categoryId),
                Set.of(tagId),
                LocalDateTime.now()
        ));

        verify(notificationService).createNotification(
                eq(recipientId),
                eq(NotificationType.BLOG_POST_PUBLISHED),
                eq("Bài blog mới từ SkillSwap"),
                eq("Nguyen A vừa đăng bài: Spring Security Guide"),
                eq("BLOG_POST"),
                eq(postId),
                eq("/blog/posts/spring-security-guide")
        );
    }
}
