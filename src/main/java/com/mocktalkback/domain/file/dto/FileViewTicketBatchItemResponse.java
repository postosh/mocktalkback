package com.mocktalkback.domain.file.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "File view ticket batch item response")
public record FileViewTicketBatchItemResponse(
    @Schema(description = "File id", example = "31")
    Long fileId,

    @Schema(description = "Variant echoed from request (null if omitted)", example = "medium")
    String variant,

    @Schema(description = "Whether ticket issue succeeded", example = "true")
    boolean success,

    @Schema(description = "View URL with ticket when protected", example = "/api/files/31/view?ticket=fv_...")
    String viewUrl,

    @Schema(description = "Ticket TTL seconds (0 for public files)", example = "120")
    long expiresInSec,

    @Schema(description = "Whether file requires ticket", example = "true")
    boolean protectedFile,

    @Schema(description = "Error code when success is false", example = "FILE_404")
    String errorCode
) {
}