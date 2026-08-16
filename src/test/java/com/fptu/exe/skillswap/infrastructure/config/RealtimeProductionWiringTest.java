package com.fptu.exe.skillswap.infrastructure.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "application.realtime.outbox.enabled=true",
        "application.websocket.stomp.enabled=true",
        "application.websocket.stomp.auto-startup=false",
        "application.websocket.stomp.relay.username=test-app",
        "application.websocket.stomp.relay.password=test-password",
        "application.websocket.stomp.client-login=test-app",
        "application.websocket.stomp.client-passcode=test-password",
        "application.websocket.stomp.system-login=test-app",
        "application.websocket.stomp.system-passcode=test-password",
        "spring.rabbitmq.listener.simple.auto-startup=false"
})
class RealtimeProductionWiringTest {

    @Test
    void contextLoadsWithBothSpringTaskSchedulers() {
    }
}
