package com.fptu.exe.skillswap.infrastructure.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.impl.DefaultClaims;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private UserBanStatusPort userBanStatusPort;

    @InjectMocks
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void regularHttpApi_queryParamTokenShouldBeIgnored() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServletPath("/api/bookings");
        request.setParameter("token", "query-jwt-should-be-ignored");

        jwtAuthenticationFilter.doFilter(
                request,
                new MockHttpServletResponse(),
                new MockFilterChain()
        );

        verify(jwtTokenProvider, never()).validateAccessToken("query-jwt-should-be-ignored");
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void websocketHandshake_queryParamTokenShouldBeAccepted() throws Exception {
        UUID userId = UUID.randomUUID();
        Claims claims = new DefaultClaims();
        claims.put("userId", userId.toString());
        claims.put("email", "socket-user@test.com");
        claims.put("roles", List.of("MENTEE"));

        when(jwtTokenProvider.validateAccessToken("socket-jwt")).thenReturn(true);
        when(jwtTokenProvider.getClaimsFromToken("socket-jwt")).thenReturn(claims);
        when(userBanStatusPort.isBanned(userId)).thenReturn(false);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServletPath("/ws");
        request.setParameter("token", "socket-jwt");

        jwtAuthenticationFilter.doFilter(
                request,
                new MockHttpServletResponse(),
                new MockFilterChain()
        );

        verify(jwtTokenProvider).validateAccessToken("socket-jwt");
        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
    }
}
