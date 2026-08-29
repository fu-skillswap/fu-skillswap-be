package com.fptu.exe.skillswap.modules.notification.strategy;

import com.fptu.exe.skillswap.modules.notification.NotificationType;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Set;

@Component
@Order(20)
public class MentorNotificationTitleResolver implements NotificationTitleResolver {

    private static final Set<NotificationType> SUPPORTED_TYPES = EnumSet.of(
            NotificationType.MENTOR_VERIFICATION_APPROVED,
            NotificationType.MENTOR_VERIFICATION_REJECTED,
            NotificationType.MENTOR_VERIFICATION_NEEDS_REVISION
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
            case MENTOR_VERIFICATION_APPROVED -> "Hồ sơ mentor của bạn đã được duyệt";
            case MENTOR_VERIFICATION_REJECTED -> "Hồ sơ mentor của bạn đã bị từ chối";
            case MENTOR_VERIFICATION_NEEDS_REVISION -> "Bạn cần bổ sung hồ sơ mentor";
            default -> fallbackTitle;
        };
    }
}
