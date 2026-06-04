package com.mocktalkback.domain.user.dto;

import java.time.Instant;

import com.mocktalkback.domain.board.type.BoardRole;
import com.mocktalkback.domain.board.type.BoardVisibility;
import com.mocktalkback.domain.file.dto.FileResponse;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "마이페이지 내 게시판 목록 아이템")
public record MyBoardItemResponse(
    @Schema(description = "멤버 ID", example = "1")
    Long id,

    @Schema(description = "게시판 ID", example = "2")
    Long boardId,

    @Schema(description = "게시판 이름", example = "자유게시판")
    String boardName,

    @Schema(description = "슬러그", example = "free-talk")
    String slug,

    @Schema(description = "설명", example = "자유롭게 이야기하는 공간")
    String description,

    @Schema(description = "공개 범위", example = "PUBLIC")
    BoardVisibility visibility,

    @Schema(description = "내 역할", example = "OWNER")
    BoardRole boardRole,

    @Schema(description = "대표 이미지")
    FileResponse boardImage,

    @Schema(description = "참여 시작 시각", example = "2024-01-01T00:00:00Z")
    Instant joinedAt
) {
}