package com.mocktalkback.global.common.dto;

import java.util.List;

import org.springframework.data.domain.Page;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "페이지 응답")
public record PageResponse<T>(
    @Schema(description = "목록")
    List<T> items,

    @Schema(description = "페이지 번호(0부터 시작)", example = "0")
    int page,

    @Schema(description = "페이지 크기", example = "10")
    int size,

    @Schema(description = "전체 개수", example = "120")
    long totalElements,

    @Schema(description = "전체 페이지 수", example = "12")
    int totalPages,

    @Schema(description = "다음 페이지 존재 여부", example = "true")
    boolean hasNext,

    @Schema(description = "이전 페이지 존재 여부", example = "false")
    boolean hasPrevious
) {
    public static <T> PageResponse<T> from(Page<T> page) {
        return from(page, page.getContent());
    }

    public static <T> PageResponse<T> from(Page<?> page, List<T> items) {
        return new PageResponse<>(
            items,
            page.getNumber(),
            page.getSize(),
            page.getTotalElements(),
            page.getTotalPages(),
            page.hasNext(),
            page.hasPrevious()
        );
    }
}
