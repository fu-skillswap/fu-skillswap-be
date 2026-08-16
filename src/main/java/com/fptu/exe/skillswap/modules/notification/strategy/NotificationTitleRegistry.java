package com.fptu.exe.skillswap.modules.notification.strategy;

import com.fptu.exe.skillswap.modules.notification.domain.NotificationType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class NotificationTitleRegistry {

    private final List<NotificationTitleResolver> resolvers;

    public NotificationTitleRegistry(List<NotificationTitleResolver> resolverList) {
        this.resolvers = resolverList != null ? new ArrayList<>(resolverList) : new ArrayList<>();
    }

    public String resolveTitle(NotificationType type, String fallbackTitle) {
        if (type == null) {
            return fallbackTitle;
        }
        for (NotificationTitleResolver resolver : resolvers) {
            if (resolver.supports(type)) {
                return resolver.resolveTitle(type, fallbackTitle);
            }
        }
        return fallbackTitle;
    }
}
