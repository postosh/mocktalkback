package com.mocktalkback.global.common.dto;

import java.time.OffsetDateTime;
import java.util.Map;

import lombok.Getter;

@Getter
public class ApiError {

    private final String code;
    private final String reason;
    private final String path;
    private final OffsetDateTime timestamp;
    private final Map<String, Object> details;

    public ApiError(String code, String reason, String path, OffsetDateTime timestamp, Map<String, Object> details) {
        this.code = code;
        this.reason = reason;
        this.path = path;
        this.timestamp = timestamp;
        this.details = details;
    }
}