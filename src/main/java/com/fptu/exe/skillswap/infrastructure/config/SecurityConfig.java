package com.fptu.exe.skillswap.infrastructure.config;

import com.fptu.exe.skillswap.infrastructure.security.JwtAuthenticationFilter;
import com.fptu.exe.skillswap.infrastructure.security.SecurityErrorResponseHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
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

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final SecurityErrorResponseHandler securityErrorResponseHandler;

    /**
     * Controls whether Swagger UI / OpenAPI docs are publicly accessible.
     *
     * <p>Set {@code APPLICATION_SWAGGER_ENABLED=false} (or omit) in production to prevent
     * anonymous users from reading the full API schema. Default is {@code true} for local dev.
     *
     * <p><b>Design decision</b>: Swagger is gated here at the security layer rather than by
     * disabling springdoc entirely, so the OpenAPI spec is still available to authenticated
     * internal tools if needed. In production, operators must explicitly opt-in by setting
     * the env var.
     */
    @Value("${application.swagger.enabled:true}")
    private boolean swaggerEnabled;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
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
                                    "/api/specializations",
                                    "/api/academic-programs/**"
                            ).permitAll();

                    // Swagger UI and API Docs — only if swagger is enabled for this environment
                    if (swaggerEnabled) {
                        auth.requestMatchers(
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html"
                        ).permitAll();
                    }

                    // Secure everything else
                    auth.anyRequest().authenticated();
                })
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
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
