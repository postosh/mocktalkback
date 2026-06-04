package com.mocktalkback.global.auth.jwt;

import java.io.IOException;

import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import tools.jackson.databind.json.JsonMapper;
import com.mocktalkback.global.common.dto.ApiEnvelope;
import com.mocktalkback.global.common.dto.ErrorCode;
import com.mocktalkback.global.i18n.ApiMessageResolver;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class JwtAuthEntryPoint implements AuthenticationEntryPoint {

    private final JsonMapper objectMapper;
    private final ApiMessageResolver messageResolver;

    public JwtAuthEntryPoint(JsonMapper objectMapper, ApiMessageResolver messageResolver) {
        this.objectMapper = objectMapper;
        this.messageResolver = messageResolver;
    }

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException
    ) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        var error = messageResolver.toApiError(ErrorCode.COMMON_UNAUTHORIZED, request.getRequestURI());
        objectMapper.writeValue(response.getWriter(), ApiEnvelope.fail(error));
    }
}
