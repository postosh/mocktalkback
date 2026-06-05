package com.mocktalkback.domain.file.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "File view ticket batch request")
public record FileViewTicketBatchRequest(
    @Schema(description = "Ticket issue targets")
    @NotNull
    @NotEmpty
    @Size(max = 100)
    List<@NotNull @Valid FileViewTicketBatchItemRequest> items
) {
}