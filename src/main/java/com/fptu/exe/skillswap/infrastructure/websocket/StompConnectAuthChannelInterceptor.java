package com.fptu.exe.skillswap.infrastructure.websocket;

import com.fptu.exe.skillswap.infrastructure.security.JwtTokenProvider;
import com.fptu.exe.skillswap.infrastructure.security.UserAuthLookupPort;
import com.fptu.exe.skillswap.infrastructure.security.UserBanStatusPort;
import com.fptu.exe.skillswap.infrastructure.security.UserPrincipal;
import com.fptu.exe.skillswap.shared.constant.RoleCode;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class StompConnectAuthChannelInterceptor implements ChannelInterceptor {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserAuthLookupPort userAuthLookupPort;
    private final UserBanStatusPort userBanStatusPort;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            Principal user = accessor.getUser();
            if (user == null || user.getName() == null || user.getName().isBlank()) {
                accessor.setUser(resolvePrincipal(accessor));
            }
            Principal resolvedUser = accessor.getUser();
            if (resolvedUser == null || resolvedUser.getName() == null || resolvedUser.getName().isBlank()) {
                throw new AccessDeniedException("STOMP session chưa được xác thực");
            }
            return MessageBuilder.createMessage(message.getPayload(), accessor.getMessageHeaders());
        } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            Principal user = accessor.getUser();
            if (user == null || user.getName() == null || user.getName().isBlank()) {
                throw new AccessDeniedException("STOMP SUBSCRIBE yêu cầu xác thực");
            }
            String destination = accessor.getDestination();
            if (destination == null || !destination.startsWith("/user/")) {
                throw new AccessDeniedException("Chỉ cho phép subscribe các destination bắt đầu bằng /user/");
            }
        }
        return message;
    }

    private Principal resolvePrincipal(StompHeaderAccessor accessor) {
        String token = extractBearerToken(accessor, "Authorization");
        if (!StringUtils.hasText(token)) {
            token = extractRawToken(accessor, "X-Access-Token");
        }
        if (!StringUtils.hasText(token) || !jwtTokenProvider.validateAccessToken(token)) {
            throw new AccessDeniedException("STOMP access token không hợp lệ");
        }
        Claims claims = jwtTokenProvider.getClaimsFromToken(token);
        UUID userId = UUID.fromString(claims.get("userId", String.class));
        if (userBanStatusPort.isBanned(userId)) {
            throw new AccessDeniedException("Tài khoản của bạn đã bị khóa");
        }
        var snapshot = userAuthLookupPort.findSnapshotByUserId(userId)
                .orElseThrow(() -> new AccessDeniedException("Không tìm thấy tài khoản người dùng"));
        List<RoleCode> roles = snapshot.roles() == null ? List.of() : snapshot.roles();
        UserPrincipal userPrincipal = UserPrincipal.create(userId, snapshot.email(), roles);
        return new StompPrincipal(userPrincipal);
    }

    private String extractBearerToken(StompHeaderAccessor accessor, String headerName) {
        String raw = extractRawToken(accessor, headerName);
        if (StringUtils.hasText(raw) && raw.startsWith("Bearer ")) {
            return raw.substring(7);
        }
        return null;
    }

    private String extractRawToken(StompHeaderAccessor accessor, String headerName) {
        List<String> values = accessor.getNativeHeader(headerName);
        if (values == null || values.isEmpty()) {
            return null;
        }
        String value = values.get(0);
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private record StompPrincipal(UserPrincipal userPrincipal) implements Principal {
        @Override
        public String getName() {
            return userPrincipal.getPublicId().toString();
        }
    }
}
