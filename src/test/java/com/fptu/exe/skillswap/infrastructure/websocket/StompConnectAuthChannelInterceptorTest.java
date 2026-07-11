package com.fptu.exe.skillswap.infrastructure.websocket;

import com.fptu.exe.skillswap.infrastructure.security.JwtTokenProvider;
import com.fptu.exe.skillswap.infrastructure.security.UserAuthLookupPort;
import com.fptu.exe.skillswap.infrastructure.security.UserAuthSnapshot;
import com.fptu.exe.skillswap.infrastructure.security.UserBanStatusPort;
import com.fptu.exe.skillswap.shared.constant.RoleCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.impl.DefaultClaims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;

import java.security.Principal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StompConnectAuthChannelInterceptorTest {

    @Mock
    private JwtTokenProvider jwtTokenProvider;
    @Mock
    private UserAuthLookupPort userAuthLookupPort;
    @Mock
    private UserBanStatusPort userBanStatusPort;

    private StompConnectAuthChannelInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new StompConnectAuthChannelInterceptor(jwtTokenProvider, userAuthLookupPort, userBanStatusPort);
    }

    @Test
    void shouldAuthenticateConnectFrameFromAuthorizationHeader() {
        UUID userId = UUID.randomUUID();
        Claims claims = new DefaultClaims();
        claims.put("userId", userId.toString());
        when(jwtTokenProvider.validateAccessToken("token-123")).thenReturn(true);
        when(jwtTokenProvider.getClaimsFromToken("token-123")).thenReturn(claims);
        when(userBanStatusPort.isBanned(userId)).thenReturn(false);
        when(userAuthLookupPort.findSnapshotByUserId(userId))
                .thenReturn(Optional.of(new UserAuthSnapshot(userId, "mentor@fpt.edu.vn", List.of(RoleCode.MENTEE))));

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setLeaveMutable(true);
        accessor.addNativeHeader("Authorization", "Bearer token-123");
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Message<?> result = interceptor.preSend(message, null);
        Principal user = StompHeaderAccessor.wrap(result).getUser();

        assertEquals(userId.toString(), user.getName());
    }

    @Test
    void shouldRejectConnectFrameWithoutValidToken() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setLeaveMutable(true);
        accessor.addNativeHeader("Authorization", "Bearer invalid");
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        when(jwtTokenProvider.validateAccessToken("invalid")).thenReturn(false);

        assertThrows(AccessDeniedException.class, () -> interceptor.preSend(message, null));
    }
}
