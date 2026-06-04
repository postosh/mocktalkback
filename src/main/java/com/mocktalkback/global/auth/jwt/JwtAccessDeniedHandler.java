package com.mocktalkback.global.auth.jwt;

import java.io.IOException;

import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import tools.jackson.databind.json.JsonMapper;
import com.mocktalkback.global.common.dto.ApiEnvelope;
import com.mocktalkback.global.common.dto.ApiError;
import com.mocktalkback.global.common.dto.ErrorCode;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class JwtAccessDeniedHandler implements AccessDeniedHandler {

    private final JsonMapper objectMapper;

    public JwtAccessDeniedHandler(JsonMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException
    ) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        ApiError error = ApiError.of(ErrorCode.COMMON_FORBIDDEN, request.getRequestURI());
        objectMapper.writeValue(response.getWriter(), ApiEnvelope.fail(error));
    }
}
