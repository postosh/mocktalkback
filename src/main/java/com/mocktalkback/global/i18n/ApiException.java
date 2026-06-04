package com.mocktalkback.global.i18n;

import com.mocktalkback.global.common.dto.ErrorCode;

import lombok.Getter;

@Getter
public class ApiException extends RuntimeException {

    private final ErrorCode errorCode;
    private final Object[] args;

    public ApiException(ErrorCode errorCode, Object... args) {
        super(errorCode.getCode());
        this.errorCode = errorCode;
        this.args = args == null ? new Object[0] : args;
    }
}