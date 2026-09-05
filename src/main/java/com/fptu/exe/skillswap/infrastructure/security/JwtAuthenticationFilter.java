package com.fptu.exe.skillswap.infrastructure.security;

import com.fptu.exe.skillswap.modules.identity.domain.UserStatus;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import com.fptu.exe.skillswap.shared.util.TraceContext;
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
    private final SecurityErrorResponseHandler securityErrorResponseHandler;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            String jwt = getJwtFromRequest(request);
            boolean validAccessToken = StringUtils.hasText(jwt) && jwtTokenProvider.validateAccessToken(jwt);

            if (StringUtils.hasText(jwt) && !validAccessToken
                    && jwtTokenProvider.isAccessTokenExpired(jwt)) {
                // Public endpoints remain usable; a protected endpoint will
                // route this marker through AuthenticationEntryPoint.
                request.setAttribute(
                        SecurityErrorResponseHandler.AUTHENTICATION_FAILURE_CODE_ATTRIBUTE,
                        ErrorCode.SESSION_EXPIRED.getCode());
            }

            if (validAccessToken) {
                Claims claims = jwtTokenProvider.getClaimsFromToken(jwt);
                
                UUID userId = UUID.fromString(claims.get("userId", String.class));
                
                // Chặn ngay user BANNED tại security filter.
                if (userBanStatusPort.isBanned(userId)) {
                    securityErrorResponseHandler.writeError(request, response, ErrorCode.USER_BANNED);
                    return;
                }
                
                var snapshot = userAuthLookupPort.findSnapshotByUserId(userId).orElse(null);
                if (snapshot == null) {
                    log.warn("Skipping authentication because user was not found in persistence correlationId={}",
                            TraceContext.getCurrentTraceId());
                    filterChain.doFilter(request, response);
                    return;
                }
                if (snapshot.status() == UserStatus.BANNED) {
                    securityErrorResponseHandler.writeError(request, response, ErrorCode.USER_BANNED);
                    return;
                }
                if (snapshot.status() == UserStatus.INACTIVE) {
                    securityErrorResponseHandler.writeError(request, response, ErrorCode.USER_INACTIVE);
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
            log.error("Could not set user authentication in security context correlationId={}",
                    TraceContext.getCurrentTraceId(), ex);
        }

        filterChain.doFilter(request, response);
    }

    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
