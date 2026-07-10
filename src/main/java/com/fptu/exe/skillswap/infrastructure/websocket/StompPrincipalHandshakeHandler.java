package com.fptu.exe.skillswap.infrastructure.websocket;

import com.fptu.exe.skillswap.infrastructure.security.UserPrincipal;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

import java.security.Principal;
import java.util.Map;
import java.util.UUID;

@Component
public class StompPrincipalHandshakeHandler extends DefaultHandshakeHandler {

    @Override
    protected Principal determineUser(ServerHttpRequest request, WebSocketHandler wsHandler, Map<String, Object> attributes) {
        Principal principal = request.getPrincipal();
        if (principal instanceof Authentication authentication
                && authentication instanceof AbstractAuthenticationToken token
                && token.isAuthenticated()) {
            return authentication;
        }

        Object principalAttribute = attributes.get(WebSocketAuthHandshakeInterceptor.USER_PRINCIPAL_ATTRIBUTE);
        if (principalAttribute instanceof UserPrincipal userPrincipal) {
            return new StompUserPrincipal(userPrincipal.getPublicId(), userPrincipal.getEmail());
        }
        Object userIdAttribute = attributes.get(WebSocketAuthHandshakeInterceptor.USER_ID_ATTRIBUTE);
        if (userIdAttribute instanceof UUID userId) {
            return new StompUserPrincipal(userId, userId.toString());
        }
        return null;
    }

    private record StompUserPrincipal(UUID userId, String name) implements Principal {
        @Override
        public String getName() {
            return userId == null ? name : userId.toString();
        }
    }
}
