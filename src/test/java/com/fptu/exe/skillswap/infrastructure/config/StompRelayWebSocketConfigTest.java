package com.fptu.exe.skillswap.infrastructure.config;

import com.fptu.exe.skillswap.infrastructure.security.JwtTokenProvider;
import com.fptu.exe.skillswap.infrastructure.security.UserAuthLookupPort;
import com.fptu.exe.skillswap.infrastructure.security.UserBanStatusPort;
import com.fptu.exe.skillswap.infrastructure.websocket.StompConnectAuthChannelInterceptor;
import com.fptu.exe.skillswap.infrastructure.websocket.StompPrincipalHandshakeHandler;
import com.fptu.exe.skillswap.infrastructure.websocket.WebSocketAuthHandshakeInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompBrokerRelayMessageHandler;
import org.springframework.messaging.SubscribableChannel;
import org.springframework.messaging.support.ExecutorSubscribableChannel;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.scheduling.concurrent.ConcurrentTaskScheduler;
import org.springframework.web.socket.config.annotation.WebMvcStompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.socket.messaging.SubProtocolWebSocketHandler;
import org.springframework.web.servlet.handler.AbstractHandlerMapping;
import org.springframework.web.servlet.handler.SimpleUrlHandlerMapping;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class StompRelayWebSocketConfigTest {

    @Test
    void shouldConfigureBrokerRelayWithoutSimpleBroker() throws Exception {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("application.cors.allowed-origin-patterns", "http://localhost:3000");
        StompRelayProperties properties = buildProperties();
        StompRelayWebSocketConfig config = new StompRelayWebSocketConfig(
                environment,
                properties,
                new WebSocketAuthHandshakeInterceptor(),
                new StompPrincipalHandshakeHandler(),
                buildAuthInterceptor()
        );

        MessageBrokerRegistry registry = new MessageBrokerRegistry(new ExecutorSubscribableChannel(), new NoOpMessageChannel());
        config.configureMessageBroker(registry);

        Collection<String> appPrefixes = invokeProtected(registry, "getApplicationDestinationPrefixes", Collection.class);
        String userPrefix = invokeProtected(registry, "getUserDestinationPrefix", String.class);
        StompBrokerRelayMessageHandler relayHandler = invokeProtected(registry, "getStompBrokerRelay", StompBrokerRelayMessageHandler.class, new ExecutorSubscribableChannel());
        Object simpleBroker = invokeProtected(registry, "getSimpleBroker", Object.class, new ExecutorSubscribableChannel());

        assertTrue(appPrefixes.contains("/app"));
        assertEquals("/user", userPrefix);
        assertNotNull(relayHandler);
        assertEquals("localhost", relayHandler.getRelayHost());
        assertEquals(61613, relayHandler.getRelayPort());
        assertFalse(relayHandler.isAutoStartup());
        assertEquals(10_000L, relayHandler.getSystemHeartbeatSendInterval());
        assertEquals(10_000L, relayHandler.getSystemHeartbeatReceiveInterval());
        assertTrue(relayHandler.getDestinationPrefixes().contains("/topic"));
        assertTrue(relayHandler.getDestinationPrefixes().contains("/queue"));
        assertEquals(null, simpleBroker);
    }

    @Test
    void shouldRegisterStompEndpoint() {
        Environment environment = new MockEnvironment()
                .withProperty("application.cors.allowed-origin-patterns", "http://localhost:3000");
        StompRelayProperties properties = buildProperties();
        StompRelayWebSocketConfig config = new StompRelayWebSocketConfig(
                environment,
                properties,
                new WebSocketAuthHandshakeInterceptor(),
                new StompPrincipalHandshakeHandler(),
                buildAuthInterceptor()
        );

        WebMvcStompEndpointRegistry registry = new WebMvcStompEndpointRegistry(
                new SubProtocolWebSocketHandler(new NoOpMessageChannel(), new ExecutorSubscribableChannel()),
                new WebSocketTransportRegistration(),
                new ConcurrentTaskScheduler()
        );

        config.registerStompEndpoints(registry);
        AbstractHandlerMapping handlerMapping = registry.getHandlerMapping();

        assertTrue(handlerMapping instanceof SimpleUrlHandlerMapping);
        Map<String, ?> urlMap = ((SimpleUrlHandlerMapping) handlerMapping).getUrlMap();
        assertTrue(urlMap.containsKey("/ws-stomp"));
    }

    private StompRelayProperties buildProperties() {
        StompRelayProperties properties = new StompRelayProperties();
        properties.setEnabled(true);
        properties.setEndpoint("/ws-stomp");
        properties.setAppDestinationPrefix("/app");
        properties.setUserDestinationPrefix("/user");
        properties.setAutoStartup(false);
        properties.setClientLogin("guest");
        properties.setClientPasscode("guest");
        properties.setSystemLogin("guest");
        properties.setSystemPasscode("guest");
        properties.setSystemHeartbeatSendIntervalMs(10_000L);
        properties.setSystemHeartbeatReceiveIntervalMs(10_000L);
        properties.getRelay().setHost("localhost");
        properties.getRelay().setPort(61613);
        properties.getRelay().setUsername("guest");
        properties.getRelay().setPassword("guest");
        return properties;
    }

    private StompConnectAuthChannelInterceptor buildAuthInterceptor() {
        return new StompConnectAuthChannelInterceptor(
                mock(JwtTokenProvider.class),
                mock(UserAuthLookupPort.class),
                mock(UserBanStatusPort.class)
        );
    }

    @SuppressWarnings("unchecked")
    private <T> T invokeProtected(Object target, String methodName, Class<T> resultType, Object... args) throws Exception {
        Method method;
        if ("getStompBrokerRelay".equals(methodName) || "getSimpleBroker".equals(methodName)) {
            method = target.getClass().getDeclaredMethod(methodName, SubscribableChannel.class);
        } else {
            method = target.getClass().getDeclaredMethod(methodName);
        }
        method.setAccessible(true);
        return (T) resultType.cast(method.invoke(target, args));
    }

    private static final class NoOpMessageChannel extends ExecutorSubscribableChannel implements MessageChannel {
    }
}
