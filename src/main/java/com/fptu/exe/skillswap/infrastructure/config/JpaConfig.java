package com.fptu.exe.skillswap.infrastructure.config;

import com.fptu.exe.skillswap.infrastructure.security.UserPrincipal;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.time.Clock;
import java.time.LocalDateTime;
import com.fptu.exe.skillswap.shared.time.TimeProvider;

@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorProvider", dateTimeProviderRef = "auditingDateTimeProvider")
public class JpaConfig {

    /**
     * Keeps Spring Data auditing on the same injected clock as booking and payment.
     * Legacy {@code LocalDateTime} audit columns remain in the business zone only
     * during the database dual-write rollout.
     */
    @Bean
    public DateTimeProvider auditingDateTimeProvider(Clock clock) {
        return () -> Optional.of(LocalDateTime.ofInstant(clock.instant(), TimeProvider.BUSINESS_ZONE));
    }

    @Bean
    public AuditorAware<String> auditorProvider() {
        return () -> {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            // Nếu chưa đăng nhập (ví dụ lúc đăng ký) hoặc là user vô danh
            if (authentication == null || !authentication.isAuthenticated()
                    || authentication.getPrincipal().equals("anonymousUser")) {
                return Optional.of("SYSTEM");
            }
            if (authentication.getPrincipal() instanceof UserPrincipal principal) {
                return Optional.ofNullable(principal.getEmail()).or(() -> Optional.of(principal.getPublicId().toString()));
            }
            return Optional.of(authentication.getName());
        };
    }
}
