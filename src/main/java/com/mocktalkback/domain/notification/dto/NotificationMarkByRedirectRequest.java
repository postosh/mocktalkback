package com.mocktalkback.domain.notification.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "알림 redirectUrl 기준 읽음 처리 요청")
public record NotificationMarkByRedirectRequest(
    @Schema(description = "읽음 처리할 알림의 리다이렉트 URL", example = "/b/general/articles/10")
    @NotBlank(message = "{validation.redirectUrl.required}")
    String redirectUrl
) {
}
