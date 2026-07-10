package com.fptu.exe.skillswap.infrastructure.config;

import com.fptu.exe.skillswap.infrastructure.filter.LegacyRawWebSocketGoneFilter;
import com.fptu.exe.skillswap.infrastructure.security.JwtAuthenticationFilter;
import com.fptu.exe.skillswap.infrastructure.security.SecurityErrorResponseHandler;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(securityErrorResponseHandler)
                        .accessDeniedHandler(securityErrorResponseHandler)
                )
                .authorizeHttpRequests(auth -> {
                    auth
                            // Public endpoints
                            .requestMatchers(
                                    "/api/auth/google",
                                    "/api/auth/refresh",
                                    "/api/auth/logout",
                                    "/api/payments/webhook/**",
                                    "/api/campuses",
                                    "/api/academic-programs",
                                    "/api/catalog/help-topics",
                                    "/api/catalog/mentor-profile-options",
                                    "/api/specializations",
                                    "/api/academic-programs/**",
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
