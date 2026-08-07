package com.fptu.exe.skillswap.infrastructure.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Component;

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
    }
}
