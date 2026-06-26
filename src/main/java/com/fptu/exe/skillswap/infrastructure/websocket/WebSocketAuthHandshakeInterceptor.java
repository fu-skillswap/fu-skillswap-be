package com.fptu.exe.skillswap.infrastructure.websocket;

import com.fptu.exe.skillswap.infrastructure.security.UserPrincipal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

@Component
@Slf4j
public class WebSocketAuthHandshakeInterceptor implements HandshakeInterceptor {

    public static final String USER_ID_ATTRIBUTE = "ws.userId";
    public static final String USER_PRINCIPAL_ATTRIBUTE = "ws.userPrincipal";

    @Override
    public boolean beforeHandshake(ServerHttpRequest request,
                                   ServerHttpResponse response,
                                   WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) {
        Authentication authentication = resolveAuthentication(request);
        if (!(authentication instanceof AbstractAuthenticationToken token) || !token.isAuthenticated()) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        Object principal = token.getPrincipal();
        if (!(principal instanceof UserPrincipal userPrincipal)) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        attributes.put(USER_ID_ATTRIBUTE, userPrincipal.getPublicId());
        attributes.put(USER_PRINCIPAL_ATTRIBUTE, userPrincipal);
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request,
                               ServerHttpResponse response,
                               WebSocketHandler wsHandler,
                               Exception exception) {
        if (exception != null) {
            log.warn("WebSocket handshake failed: {}", exception.getMessage());
        }
    }

    private Authentication resolveAuthentication(ServerHttpRequest request) {
        if (request.getPrincipal() instanceof Authentication authentication) {
            return authentication;
        }
        if (request instanceof ServletServerHttpRequest servletRequest
                && servletRequest.getServletRequest().getUserPrincipal() instanceof Authentication authentication) {
            return authentication;
        }
        return SecurityContextHolder.getContext().getAuthentication();
    }
}
