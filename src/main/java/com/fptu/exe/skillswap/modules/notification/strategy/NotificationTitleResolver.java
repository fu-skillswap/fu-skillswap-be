package com.fptu.exe.skillswap.modules.notification.strategy;

import com.fptu.exe.skillswap.modules.notification.domain.NotificationType;

public interface NotificationTitleResolver {

    boolean supports(NotificationType type);

    String resolveTitle(NotificationType type, String fallbackTitle);
}
