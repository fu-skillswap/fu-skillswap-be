package com.fptu.exe.skillswap.infrastructure.config;

import com.fptu.exe.skillswap.infrastructure.filter.LegacyRawWebSocketGoneFilter;
import com.fptu.exe.skillswap.infrastructure.security.JwtAuthenticationFilter;
import com.fptu.exe.skillswap.infrastructure.security.SecurityErrorResponseHandler;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.util.matcher.RegexRequestMatcher;
import org.springframework.web.filter.CorsFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final SecurityErrorResponseHandler securityErrorResponseHandler;
    private final LegacyRawWebSocketGoneFilter legacyRawWebSocketGoneFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .headers(headers -> headers
                        .frameOptions(frame -> frame.deny())
                        .contentTypeOptions(Customizer.withDefaults())
                        .referrerPolicy(policy -> policy
                                .policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        .contentSecurityPolicy(csp -> csp
                                .policyDirectives("default-src 'none'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; font-src 'self' data:; connect-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'"))
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .preload(true)
                                .maxAgeInSeconds(31_536_000)))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(securityErrorResponseHandler)
                        .accessDeniedHandler(securityErrorResponseHandler)
                )
                .authorizeHttpRequests(auth -> {
                    auth
                            // Public endpoints
                            .requestMatchers(
                                    "/api/auth/google",
                                    "/api/auth/google/authorization-context",
                                    "/api/auth/refresh",
                                    "/api/auth/logout",
                                    "/api/payments/webhook/**",
                                    "/uploads/storage/**",
                                    "/health",
                                    "/actuator/health",
                                    "/actuator/health/**",
                                    "/livez",
                                    "/readyz",
                                    "/ws-stomp",
                                    "/ws-stomp/**",
                                    "/v3/api-docs/**",
                                    "/swagger-ui/**",
                                    "/swagger-ui.html"
                            ).permitAll();
                    auth
                            .requestMatchers(HttpMethod.GET,
                                    "/api/campuses",
                                    "/api/academic-programs",
                                    "/api/academic-programs/*/specializations",
                                    "/api/specializations",
                                    "/api/catalog/help-topics",
                                    "/api/catalog/mentor-profile-options",
                                    "/api/blog/posts",
                                    "/api/blog/posts/*",
                                    "/api/blog/posts/*/related",
                                    "/api/blog/posts/*/recommendations",
                                    "/api/blog/featured",
                                    "/api/blog/trending",
                                    "/api/blog/categories",
                                    "/api/blog/tags",
                                    "/api/mentors"
                            ).permitAll();
                    auth
                            .requestMatchers(
                                    RegexRequestMatcher.regexMatcher(HttpMethod.GET, "^/api/mentors/[0-9a-fA-F-]{36}$"),
                                    RegexRequestMatcher.regexMatcher(HttpMethod.GET, "^/api/mentors/[0-9a-fA-F-]{36}/reviews$")
                            ).permitAll();
                    auth
                            .requestMatchers(HttpMethod.POST,
                                    "/api/blog/posts/*/view"
                            ).permitAll();

                    // Secure everything else
                    auth.anyRequest().authenticated();
                })
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        http.addFilterAfter(legacyRawWebSocketGoneFilter, CorsFilter.class);
        http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public FilterRegistrationBean<LegacyRawWebSocketGoneFilter> legacyRawWebSocketGoneFilterRegistration() {
        FilterRegistrationBean<LegacyRawWebSocketGoneFilter> registration = new FilterRegistrationBean<>(legacyRawWebSocketGoneFilter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public UserDetailsService userDetailsService() {
        return username -> {
            throw new UsernameNotFoundException("Hệ thống hiện không hỗ trợ đăng nhập bằng tài khoản và mật khẩu");
        };
    }
}
