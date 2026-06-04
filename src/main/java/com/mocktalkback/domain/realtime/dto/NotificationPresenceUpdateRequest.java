package com.mocktalkback.domain.realtime.dto;

import com.mocktalkback.domain.realtime.type.NotificationPresenceViewType;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(description = "알림 presence 업데이트 요청")
public record NotificationPresenceUpdateRequest(
    @Schema(description = "브라우저 탭 단위 세션 아이디", example = "2db40ef2-2b49-4ef0-a53f-30fddf0f4d8a")
    @NotBlank(message = "{validation.sessionId.required}")
    String sessionId,

    @Schema(description = "현재 화면 유형", example = "ARTICLE_DETAIL")
    @NotNull(message = "{validation.viewType.required}")
    NotificationPresenceViewType viewType,

    @Schema(description = "현재 조회 중인 게시글 아이디", example = "101")
    Long articleId,

    @Schema(description = "알림 패널 열림 여부", example = "true")
    boolean notificationPanelOpen
) {
}
