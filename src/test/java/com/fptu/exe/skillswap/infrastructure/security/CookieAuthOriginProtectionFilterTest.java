package com.fptu.exe.skillswap.infrastructure.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CookieAuthOriginProtectionFilterTest {

    private final CookieAuthOriginProtectionFilter filter = new CookieAuthOriginProtectionFilter(
            new MockEnvironment().withProperty("application.cors.allowed-origin-patterns", "https://app.skillswap.vn"),
            new SecurityErrorResponseHandler(new ObjectMapper().findAndRegisterModules())
    );

    @Test
    void refreshFromAllowedFrontendOriginPasses() throws Exception {
        MockHttpServletRequest request = postRequest("/api/auth/refresh", "https://app.skillswap.vn");
        AtomicBoolean proceeded = new AtomicBoolean(false);

        filter.doFilter(request, new MockHttpServletResponse(), (ignoredRequest, ignoredResponse) -> proceeded.set(true));

        assertTrue(proceeded.get());
    }

    @Test
    void refreshWithoutOriginIsRejected() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean proceeded = new AtomicBoolean(false);

        filter.doFilter(postRequest("/api/auth/refresh", null), response,
                (ignoredRequest, ignoredResponse) -> proceeded.set(true));

        assertFalse(proceeded.get());
        assertTrue(response.getStatus() == 403);
    }

    @Test
    void refreshWithoutOriginFallsBackToAllowedReferer() throws Exception {
        MockHttpServletRequest request = postRequest("/api/auth/refresh", null);
        request.addHeader("Referer", "https://app.skillswap.vn/login?returnTo=%2Fbookings");
        AtomicBoolean proceeded = new AtomicBoolean(false);

        filter.doFilter(request, new MockHttpServletResponse(), (ignoredRequest, ignoredResponse) -> proceeded.set(true));

        assertTrue(proceeded.get());
    }

    @Test
    void logoutFromForeignOriginIsRejected() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean proceeded = new AtomicBoolean(false);

        filter.doFilter(postRequest("/api/auth/logout", "https://attacker.example"), response,
                (ignoredRequest, ignoredResponse) -> proceeded.set(true));

        assertFalse(proceeded.get());
        assertTrue(response.getStatus() == 403);
    }

    @Test
    void foreignOriginCannotBypassProtectionWithAllowedReferer() throws Exception {
        MockHttpServletRequest request = postRequest("/api/auth/refresh", "https://attacker.example");
        request.addHeader("Referer", "https://app.skillswap.vn/account");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean proceeded = new AtomicBoolean(false);

        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> proceeded.set(true));

        assertFalse(proceeded.get());
        assertTrue(response.getStatus() == 403);
    }

    private MockHttpServletRequest postRequest(String path, String origin) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.setServletPath(path);
        if (origin != null) {
            request.addHeader("Origin", origin);
        }
        return request;
    }
}
