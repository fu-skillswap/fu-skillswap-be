package com.fptu.exe.skillswap.infrastructure.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;

/**
 * Chỉ lấy IP client khi peer trực tiếp là reverse proxy đã cấu hình.
 * Bỏ qua forwarded header từ client đi thẳng từ Internet.
 */
@Component
public class TrustedClientIpResolver {

    private final List<IpAddressMatcher> trustedProxyMatchers;

    public TrustedClientIpResolver(
            @Value("${application.security.trusted-proxy-cidrs:}") String trustedProxyCidrs
    ) {
        this.trustedProxyMatchers = Arrays.stream(trustedProxyCidrs.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .map(IpAddressMatcher::new)
                .toList();
    }

    public String resolve(HttpServletRequest request) {
        if (request == null || !StringUtils.hasText(request.getRemoteAddr())) {
            return "unknown";
        }

        String remoteAddress = request.getRemoteAddr();
        if (!isTrustedProxy(remoteAddress)) {
            return remoteAddress;
        }

        String cloudflareAddress = request.getHeader("CF-Connecting-IP");
        if (isUsableAddress(cloudflareAddress)) {
            return cloudflareAddress.trim();
        }

        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwardedFor)) {
            for (String candidate : forwardedFor.split(",")) {
                if (isUsableAddress(candidate)) {
                    return candidate.trim();
                }
            }
        }
        return remoteAddress;
    }

    private boolean isTrustedProxy(String remoteAddress) {
        return trustedProxyMatchers.stream().anyMatch(matcher -> matcher.matches(remoteAddress));
    }

    private boolean isUsableAddress(String value) {
        return StringUtils.hasText(value) && value.trim().length() <= 45;
    }
}
