package com.fptu.exe.skillswap.infrastructure.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Prevents a partially enabled realtime topology from silently losing delivery. */
@Component
@RequiredArgsConstructor
public class RealtimeMessagingConfigurationValidator implements SmartInitializingSingleton {

    private final RealtimeOutboxProperties realtimeOutboxProperties;
    private final StompRelayProperties stompRelayProperties;

    @Override
    public void afterSingletonsInstantiated() {
        if (realtimeOutboxProperties.isEnabled() != stompRelayProperties.isEnabled()) {
            throw new IllegalStateException(
                    "REALTIME_OUTBOX_ENABLED and WEBSOCKET_STOMP_ENABLED must be enabled or disabled together");
        }

        if (stompRelayProperties.isEnabled()
                && (!StringUtils.hasText(stompRelayProperties.getRelay().getUsername())
                || !StringUtils.hasText(stompRelayProperties.getRelay().getPassword())
                || isGuestCredential(stompRelayProperties.getRelay().getUsername())
                || isGuestCredential(stompRelayProperties.getRelay().getPassword())
                || isGuestCredential(stompRelayProperties.getClientLogin())
                || isGuestCredential(stompRelayProperties.getClientPasscode())
                || isGuestCredential(stompRelayProperties.getSystemLogin())
                || isGuestCredential(stompRelayProperties.getSystemPasscode()))) {
            throw new IllegalStateException(
                    "STOMP relay must use non-empty RabbitMQ application credentials, never guest/guest");
        }
    }

    private boolean isGuestCredential(String value) {
        return StringUtils.hasText(value) && "guest".equalsIgnoreCase(value.trim());
    }
}
