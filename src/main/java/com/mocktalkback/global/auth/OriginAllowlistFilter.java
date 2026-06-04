package com.mocktalkback.global.auth;

import java.io.IOException;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import tools.jackson.databind.json.JsonMapper;
import com.mocktalkback.global.common.dto.ApiEnvelope;
import com.mocktalkback.global.common.dto.ErrorCode;
import com.mocktalkback.global.i18n.ApiMessageResolver;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class OriginAllowlistFilter extends OncePerRequestFilter {

    private final Set<String> allowedOrigins;
    private final JsonMapper objectMapper;
    private final ApiMessageResolver messageResolver;

    public OriginAllowlistFilter(
            @Value("${SECURITY_ORIGIN_ALLOWLIST:}") String allowlist,
            JsonMapper objectMapper,
            ApiMessageResolver messageResolver
    ) {
        this.allowedOrigins = parseAllowlist(allowlist);
        this.objectMapper = objectMapper;
        this.messageResolver = messageResolver;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String path = request.getServletPath();
        return !("/api/auth/refresh".equals(path) || "/api/auth/logout".equals(path));
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String origin = request.getHeader("Origin");
        if (!StringUtils.hasText(origin) || !allowedOrigins.contains(origin)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            var error = messageResolver.toApiError(
                    ErrorCode.COMMON_FORBIDDEN,
                    messageResolver.resolveKey("error.origin.invalid"),
                    request.getRequestURI(),
                    null
            );
            objectMapper.writeValue(response.getWriter(), ApiEnvelope.fail(error));
            return;
        }
        filterChain.doFilter(request, response);
    }

    private Set<String> parseAllowlist(String allowlist) {
        if (!StringUtils.hasText(allowlist)) {
            return Set.of();
        }
        return Arrays.stream(allowlist.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .collect(Collectors.toUnmodifiableSet());
    }
}
