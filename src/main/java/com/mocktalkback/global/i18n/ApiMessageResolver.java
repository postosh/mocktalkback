package com.mocktalkback.global.i18n;

import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Map;

import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

import com.mocktalkback.global.common.dto.ApiError;
import com.mocktalkback.global.common.dto.ErrorCode;

@Component
public class ApiMessageResolver {

    private final MessageSource messageSource;

    public ApiMessageResolver(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    public String resolve(ErrorCode errorCode, Object... args) {
        return resolveKey(errorCode.getMessageKey(), args);
    }

    public String resolveKey(String key, Object... args) {
        Locale locale = LocaleContextHolder.getLocale();
        return messageSource.getMessage(key, args, key, locale);
    }

    public ApiError toApiError(ErrorCode errorCode, String path) {
        return toApiError(errorCode, null, path, null);
    }

    public ApiError toApiError(ErrorCode errorCode, String reasonOverride, String path, Map<String, Object> details) {
        return toApiError(errorCode, reasonOverride, path, details, new Object[0]);
    }

    public ApiError toApiError(
            ErrorCode errorCode,
            String reasonOverride,
            String path,
            Map<String, Object> details,
            Object... args
    ) {
        String reason = resolveReason(errorCode, reasonOverride, args);
        return new ApiError(errorCode.getCode(), reason, path, OffsetDateTime.now(), details);
    }

    private String resolveReason(ErrorCode errorCode, String reasonOverride, Object... args) {
        if (reasonOverride == null || reasonOverride.isBlank()) {
            return resolve(errorCode, args);
        }
        return reasonOverride;
    }
}