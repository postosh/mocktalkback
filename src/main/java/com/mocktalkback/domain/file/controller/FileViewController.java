package com.mocktalkback.domain.file.controller;

import java.net.URI;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mocktalkback.domain.file.dto.FileViewTicketBatchRequest;
import com.mocktalkback.domain.file.dto.FileViewTicketBatchResponse;
import com.mocktalkback.domain.file.dto.FileViewTicketResponse;

import jakarta.validation.Valid;
import com.mocktalkback.domain.file.service.FileViewService;
import com.mocktalkback.domain.file.service.FileViewTicketService;
import com.mocktalkback.global.common.dto.ApiEnvelope;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
@Tag(name = "FileView", description = "파일 조회 리다이렉트 API")
public class FileViewController {

    private final FileViewService fileViewService;
    private final FileViewTicketService fileViewTicketService;

    @PostMapping("/files/view-tickets")
    @Operation(
        summary = "파일 보기 ticket 배치 발급",
        description = "게시글 본문 등 다수 미디어 URL용. 항목별 성공/실패를 반환하며, 권한 없음/없는 파일은 FILE_404로 표시합니다."
    )
    public ApiEnvelope<FileViewTicketBatchResponse> issueViewTickets(
        @Valid @RequestBody FileViewTicketBatchRequest request
    ) {
        FileViewTicketBatchResponse response = fileViewTicketService.issueBatch(request.items());
        return ApiEnvelope.ok(response);
    }

    @PostMapping("/files/{fileId:\\d+}/view-ticket")
    @Operation(summary = "파일 보기 ticket 발급", description = "보호 파일은 짧은 TTL 동안 재사용 가능한 ticket을 포함한 보기 URL을 발급하고, 공개 파일은 기존 보기 URL을 반환합니다.")
    public ApiEnvelope<FileViewTicketResponse> issueViewTicket(
        @PathVariable("fileId") Long fileId,
        @RequestParam(name = "variant", required = false) String variant
    ) {
        FileViewTicketResponse response = fileViewTicketService.issue(fileId, variant);
        return ApiEnvelope.ok(response);
    }

    @GetMapping("/files/{fileId:\\d+}/view")
    @Operation(summary = "파일 보기", description = "최적화본이 있으면 변환본으로, 없으면 원본으로 리다이렉트합니다.")
    public ResponseEntity<Void> viewFile(
        @PathVariable("fileId") Long fileId,
        @RequestParam(name = "variant", required = false) String variant,
        @RequestParam(name = "ticket", required = false) String ticket
    ) {
        String location = fileViewService.resolveViewLocation(fileId, variant, ticket);
        HttpHeaders headers = new HttpHeaders();
        headers.setLocation(URI.create(location));
        return new ResponseEntity<>(headers, HttpStatus.FOUND);
    }
}
