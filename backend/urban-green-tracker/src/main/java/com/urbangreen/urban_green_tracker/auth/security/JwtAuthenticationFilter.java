package com.urbangreen.urban_green_tracker.auth.security;

import com.nimbusds.jwt.JWTClaimsSet;
import com.urbangreen.urban_green_tracker.auth.service.TokenService;
import com.urbangreen.urban_green_tracker.exception.AppException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;   

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final TokenService tokenService;
    private final JsonMapper jsonMapper;         

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        // Không có token → cho đi tiếp (SecurityConfig sẽ quyết định)
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);

        try {
            JWTClaimsSet claims = tokenService.verifyAccessToken(token);

            String userId = claims.getSubject();
            String scope = claims.getStringClaim("scope");

            // Chặn trường hợp scope bị null → tránh 500
            if (scope == null || scope.isBlank()) {
                throw new AppException(
                    com.urbangreen.urban_green_tracker.exception.ErrorCode.UNAUTHENTICATED
                );
            }

            SimpleGrantedAuthority authority = new SimpleGrantedAuthority(scope);

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            userId,
                            null,
                            Collections.singletonList(authority)
                    );

            SecurityContextHolder.getContext().setAuthentication(authentication);
            filterChain.doFilter(request, response);

        } catch (AppException e) {
            SecurityContextHolder.clearContext();

            response.setStatus(e.getErrorCode().getStatusCode().value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("code", e.getErrorCode().getCode());
            errorResponse.put("message", e.getErrorCode().getMessage());

            jsonMapper.writeValue(response.getWriter(), errorResponse);

        } catch (Exception e) {
            log.error("Lỗi không xác định tại JwtAuthenticationFilter", e);
            SecurityContextHolder.clearContext();   
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Lỗi Server");
        }
    }
}