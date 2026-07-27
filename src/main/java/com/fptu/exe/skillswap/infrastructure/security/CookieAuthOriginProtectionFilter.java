package com.fptu.exe.skillswap.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * CSRF boundary for the only browser-cookie authenticated mutations. Business APIs
 * use an explicit bearer token and remain stateless, so they do not need CSRF tokens.
 */
@Component
public class CookieAuthOriginProtectionFilter extends OncePerRequestFilter {

    private static final Set<String> PROTECTED_PATHS = Set.of("/api/auth/refresh", "/api/auth/logout");

    private final Set<String> allowedOrigins;
    private final SecurityErrorResponseHandler securityErrorResponseHandler;

    public CookieAuthOriginProtectionFilter(
            Environment environment,
            SecurityErrorResponseHandler securityErrorResponseHandler
    ) {
        this.allowedOrigins = Arrays.stream(environment.getProperty(
                        "application.cors.allowed-origin-patterns", "").split(","))
                .map(this::normalizeOrigin)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toUnmodifiableSet());
        this.securityErrorResponseHandler = securityErrorResponseHandler;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equalsIgnoreCase(request.getMethod())
                || !PROTECTED_PATHS.contains(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String origin = request.getHeader(HttpHeaders.ORIGIN);
        boolean allowed = StringUtils.hasText(origin)
                ? isAllowedOrigin(origin)
                : isAllowedOrigin(request.getHeader(HttpHeaders.REFERER));
        if (!allowed) {
            securityErrorResponseHandler.handle(
                    request,
                    response,
                    new AccessDeniedException("Cookie-auth request must originate from an allowed frontend origin")
            );
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean isAllowedOrigin(String value) {
        String origin = normalizeOrigin(value);
        return origin != null && allowedOrigins.contains(origin);
    }

    private String normalizeOrigin(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            URI uri = URI.create(value.trim());
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (scheme == null || host == null || uri.getRawUserInfo() != null
                    || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))) {
                return null;
            }
            String port = uri.getPort() < 0 ? "" : ":" + uri.getPort();
            return scheme.toLowerCase(Locale.ROOT) + "://" + host.toLowerCase(Locale.ROOT) + port;
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
