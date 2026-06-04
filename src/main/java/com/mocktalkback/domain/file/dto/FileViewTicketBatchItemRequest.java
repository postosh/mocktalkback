package com.mocktalkback.domain.file.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@Schema(description = "File view ticket batch item request")
public record FileViewTicketBatchItemRequest(
    @Schema(description = "File id", example = "31")
    @NotNull
    @Positive
    Long fileId,

    @Schema(description = "Variant (optional)", example = "medium")
    String variant
) {
}