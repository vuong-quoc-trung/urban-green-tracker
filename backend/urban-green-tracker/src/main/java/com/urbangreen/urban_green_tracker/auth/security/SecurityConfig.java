package com.urbangreen.urban_green_tracker.auth.security;

import com.urbangreen.urban_green_tracker.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import tools.jackson.databind.json.JsonMapper;

import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JsonMapper jsonMapper;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // 1. Tắt CSRF
                .csrf(AbstractHttpConfigurer::disable)

                // 2. Chạy 100% Stateless
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // 3. Xử lý ngoại lệ bảo mật
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(ErrorCode.UNAUTHENTICATED.getStatusCode().value());
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.setCharacterEncoding("UTF-8");

                            Map<String, Object> body = new HashMap<>();
                            body.put("code", ErrorCode.UNAUTHENTICATED.getCode());
                            body.put("message", "Vui lòng đăng nhập để thực hiện chức năng này");

                            jsonMapper.writeValue(response.getWriter(), body);
                        }))

                // 4. Định tuyến và Phân quyền API
                .authorizeHttpRequests(auth -> auth
                        // ===== Public APIs =====
                        .requestMatchers(
                                "/api/v1/auth/login",
                                "/api/v1/auth/register",
                                "/api/v1/auth/refresh",
                                "/api/v1/auth/forgot-password",
                                "/api/v1/auth/reset-password",
                                "/api/v1/auth/google",
                                "/error",
                                "/api/v1/auth/logout",
                                "/api/v1/auth/logout-all")
                        .permitAll()

                        // ===== Admin / Manager =====
                        .requestMatchers("/api/v1/admin/**")
                        .hasAnyAuthority("ROLE_ADMIN", "ROLE_MANAGER")

                        // ===== Need Authentication =====
                        .anyRequest().authenticated())

                // 5. Thêm Trạm gác (Filter)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}