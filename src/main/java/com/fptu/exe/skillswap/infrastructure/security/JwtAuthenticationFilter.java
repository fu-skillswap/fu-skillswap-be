package com.fptu.exe.skillswap.infrastructure.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserAuthLookupPort userAuthLookupPort;
    private final UserBanStatusPort userBanStatusPort;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            String jwt = getJwtFromRequest(request);

            if (StringUtils.hasText(jwt) && jwtTokenProvider.validateAccessToken(jwt)) {
                Claims claims = jwtTokenProvider.getClaimsFromToken(jwt);
                
                UUID userId = UUID.fromString(claims.get("userId", String.class));
                
                // Block BANNED users immediately at the security filter layer
                if (userBanStatusPort.isBanned(userId)) {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType("application/json");
                    response.setCharacterEncoding("UTF-8");
                    response.getWriter().write("{\"code\":\"AUTH_1004\",\"message\":\"Tài khoản của bạn đã bị khóa\"}");
                    return;
                }
                
                var snapshot = userAuthLookupPort.findSnapshotByUserId(userId).orElse(null);
                if (snapshot == null) {
                    log.warn("Skipping authentication because user {} was not found in persistence", userId);
                    filterChain.doFilter(request, response);
                    return;
                }

                String email = snapshot.email();
                List<com.fptu.exe.skillswap.shared.constant.RoleCode> roles = snapshot.roles() == null
                        ? List.of()
                        : snapshot.roles();

                UserPrincipal userPrincipal = UserPrincipal.create(userId, email, roles);

                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        userPrincipal, null, userPrincipal.getAuthorities());
                
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } catch (Exception ex) {
            log.error("Could not set user authentication in security context", ex);
        }

        filterChain.doFilter(request, response);
    }

    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        // ?token= query param is ONLY accepted for the raw WebSocket handshake endpoint.
        // Regular HTTP APIs must use the Authorization header.
        // Allowing ?token= on HTTP APIs leaks JWTs into server logs, proxy access logs,
        // browser history, and Referer headers.
        String requestPath = request.getServletPath();
        if (requestPath != null && (requestPath.startsWith("/ws/") || requestPath.equals("/ws"))) {
            String tokenParam = request.getParameter("token");
            if (StringUtils.hasText(tokenParam)) {
                return tokenParam;
            }
        }
        return null;
    }
}
