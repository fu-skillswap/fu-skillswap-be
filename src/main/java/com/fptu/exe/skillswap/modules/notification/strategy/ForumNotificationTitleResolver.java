package com.fptu.exe.skillswap.modules.notification.strategy;

import com.fptu.exe.skillswap.modules.notification.NotificationType;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Set;

@Component
@Order(30)
public class ForumNotificationTitleResolver implements NotificationTitleResolver {

    private static final Set<NotificationType> SUPPORTED_TYPES = EnumSet.of(
            NotificationType.FORUM_POST_COMMENTED,
            NotificationType.FORUM_COMMENT_REPLY,
            NotificationType.FORUM_POST_HIDDEN,
            NotificationType.FORUM_COMMENT_HIDDEN,
            NotificationType.BLOG_POST_PUBLISHED,
            NotificationType.CHAT_UNREAD,
            NotificationType.ACCOUNT_UNLOCKED,
            NotificationType.ADMIN_DISPUTE_SLA_BREACH
    );

    @Override
    public boolean supports(NotificationType type) {
        return type != null && SUPPORTED_TYPES.contains(type);
    }

    @Override
    public String resolveTitle(NotificationType type, String fallbackTitle) {
        if (type == null) {
            return fallbackTitle;
        }
        return switch (type) {
            case FORUM_POST_COMMENTED -> "Bài viết có bình luận mới";
            case FORUM_COMMENT_REPLY -> "Bình luận của bạn có người trả lời";
            case FORUM_POST_HIDDEN -> "Bài viết đã bị ẩn";
            case FORUM_COMMENT_HIDDEN -> "Bình luận đã bị ẩn";
            case BLOG_POST_PUBLISHED -> "Bài blog mới từ SkillSwap";
            case CHAT_UNREAD -> "Tin nhắn mới";
            case ACCOUNT_UNLOCKED -> "Tài khoản đã được mở khóa";
            case ADMIN_DISPUTE_SLA_BREACH -> "Cảnh báo SLA Khiếu nại";
            default -> fallbackTitle;
        };
    }
}
