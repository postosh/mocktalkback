package com.mocktalkback.domain.file.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "File view ticket batch response")
public record FileViewTicketBatchResponse(
    @Schema(description = "Per-item ticket issue results in request order")
    List<FileViewTicketBatchItemResponse> items
) {
}