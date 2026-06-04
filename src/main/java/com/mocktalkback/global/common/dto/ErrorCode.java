package com.mocktalkback.global.common.dto;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {

    // ===== Common =====
    COMMON_BAD_REQUEST("COMMON_400", HttpStatus.BAD_REQUEST, "잘못된 요청입니다."),
    COMMON_UNAUTHORIZED("COMMON_401", HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    COMMON_FORBIDDEN("COMMON_403", HttpStatus.FORBIDDEN, "권한이 없습니다."),
    COMMON_NOT_FOUND("COMMON_404", HttpStatus.NOT_FOUND, "리소스를 찾을 수 없습니다."),
    COMMON_METHOD_NOT_ALLOWED("COMMON_405", HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 HTTP 메서드입니다."),
    COMMON_CONFLICT("COMMON_409", HttpStatus.CONFLICT, "요청이 충돌했습니다."),
    COMMON_TOO_MANY_REQUESTS("COMMON_429", HttpStatus.TOO_MANY_REQUESTS, "요청이 너무 많습니다."),
    COMMON_PAYLOAD_TOO_LARGE("COMMON_413", HttpStatus.CONTENT_TOO_LARGE, "요청 본문이 너무 큽니다."),
    COMMON_INTERNAL_ERROR("COMMON_500", HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다."),

    // ===== Auth / JWT =====
    AUTH_INVALID_CREDENTIALS("AUTH_001", HttpStatus.UNAUTHORIZED, "아이디 또는 비밀번호가 올바르지 않습니다."),
    AUTH_TOKEN_MISSING("AUTH_010", HttpStatus.UNAUTHORIZED, "인증 토큰이 없습니다."),
    AUTH_TOKEN_INVALID("AUTH_011", HttpStatus.UNAUTHORIZED, "인증 토큰이 유효하지 않습니다."),
    AUTH_TOKEN_EXPIRED("AUTH_012", HttpStatus.UNAUTHORIZED, "인증 토큰이 만료되었습니다."),
    AUTH_REFRESH_EXPIRED("AUTH_013", HttpStatus.UNAUTHORIZED, "리프레시 토큰이 만료되었습니다."),

    // ===== OAuth2 =====
    OAUTH2_PROVIDER_NOT_SUPPORTED("OAUTH2_001", HttpStatus.BAD_REQUEST, "지원하지 않는 소셜 로그인 제공자입니다."),
    OAUTH2_EMAIL_NOT_AVAILABLE("OAUTH2_002", HttpStatus.BAD_REQUEST, "소셜 계정에서 이메일을 가져올 수 없습니다."),

    // ===== User =====
    USER_NOT_FOUND("USER_404", HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),
    USER_EMAIL_NOT_VERIFIED("USER_010", HttpStatus.FORBIDDEN, "이메일 인증이 필요합니다."),
    USER_HANDLE_ALREADY_EXISTS("USER_020", HttpStatus.CONFLICT, "이미 사용 중인 핸들입니다.");

    private final String code;
    private final HttpStatus httpStatus;
    private final String defaultMessage;

    ErrorCode(String code, HttpStatus httpStatus, String defaultMessage) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }
}
