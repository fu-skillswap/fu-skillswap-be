package com.fptu.exe.skillswap.infrastructure.config;

import com.fptu.exe.skillswap.infrastructure.websocket.StompConnectAuthChannelInterceptor;
import com.fptu.exe.skillswap.infrastructure.websocket.StompPrincipalHandshakeHandler;
import com.fptu.exe.skillswap.infrastructure.websocket.WebSocketAuthHandshakeInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSocketMessageBroker
@ConditionalOnProperty(prefix = "application.websocket.stomp", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class StompRelayWebSocketConfig implements WebSocketMessageBrokerConfigurer {

    public static final String APP_DESTINATION_PREFIX = "/app";
    public static final String USER_DESTINATION_PREFIX = "/user";
    public static final String[] BROKER_DESTINATION_PREFIXES = {"/topic", "/queue"};

    private final Environment environment;
    private final StompRelayProperties properties;
    private final WebSocketAuthHandshakeInterceptor webSocketAuthHandshakeInterceptor;
    private final StompPrincipalHandshakeHandler stompPrincipalHandshakeHandler;
    private final StompConnectAuthChannelInterceptor stompConnectAuthChannelInterceptor;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.setApplicationDestinationPrefixes(properties.getAppDestinationPrefix());
        registry.setUserDestinationPrefix(properties.getUserDestinationPrefix());
        registry.enableStompBrokerRelay(BROKER_DESTINATION_PREFIXES)
                .setRelayHost(properties.getRelay().getHost())
                .setRelayPort(properties.getRelay().getPort())
                .setClientLogin(resolveLogin(properties.getClientLogin(), properties.getRelay().getUsername()))
                .setClientPasscode(resolveSecret(properties.getClientPasscode(), properties.getRelay().getPassword()))
                .setSystemLogin(resolveLogin(properties.getSystemLogin(), properties.getRelay().getUsername()))
                .setSystemPasscode(resolveSecret(properties.getSystemPasscode(), properties.getRelay().getPassword()))
                .setSystemHeartbeatSendInterval(properties.getSystemHeartbeatSendIntervalMs())
                .setSystemHeartbeatReceiveInterval(properties.getSystemHeartbeatReceiveIntervalMs())
                .setAutoStartup(properties.isAutoStartup())
                .setVirtualHost("/")
                .setUserDestinationBroadcast("/topic/unresolved-user-destination")
                .setUserRegistryBroadcast("/topic/simp-user-registry");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint(properties.getEndpoint())
                .addInterceptors(webSocketAuthHandshakeInterceptor)
                .setHandshakeHandler(stompPrincipalHandshakeHandler)
                .setAllowedOriginPatterns(resolveAllowedOriginPatterns());
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(stompConnectAuthChannelInterceptor);
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registry) {
        registry.setMessageSizeLimit(properties.getMessageSizeLimit());
        registry.setSendBufferSizeLimit(properties.getSendBufferSizeLimit());
        registry.setSendTimeLimit(properties.getSendTimeLimitMs());
    }

    private String[] resolveAllowedOriginPatterns() {
        String patterns = environment.getProperty("application.cors.allowed-origin-patterns", "");
        List<String> values = Arrays.stream(patterns.split(","))
                .map(String::trim)
                .filter(pattern -> !pattern.isEmpty())
                .toList();
        return values.toArray(String[]::new);
    }

    private String resolveLogin(String primary, String fallback) {
        return StringUtils.hasText(primary) ? primary : fallback;
    }

    private String resolveSecret(String primary, String fallback) {
        return StringUtils.hasText(primary) ? primary : fallback;
    }
}
