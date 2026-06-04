package com.mocktalkback.domain.file.service;

import com.mocktalkback.global.common.dto.ErrorCode;
import com.mocktalkback.global.i18n.ApiException;
import java.time.Duration;
import java.util.Optional;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.util.StringUtils;

import com.mocktalkback.domain.file.dto.FileViewTicketBatchItemRequest;
import com.mocktalkback.domain.file.dto.FileViewTicketBatchItemResponse;
import com.mocktalkback.domain.file.dto.FileViewTicketBatchResponse;
import com.mocktalkback.domain.file.dto.FileViewTicketResponse;
import com.mocktalkback.domain.file.entity.FileEntity;
import com.mocktalkback.domain.file.repository.FileRepository;
import com.mocktalkback.global.auth.ticket.TicketIdGenerator;
import com.mocktalkback.infra.storage.ObjectStorageProperties;

@Service
public class FileViewTicketService {

    private static final String TICKET_PREFIX = "fv_";
    private static final long DEFAULT_PROTECTED_VIEW_EXPIRE_SECONDS = 120L;
    private static final long MAX_TICKET_TTL_SECONDS = 604800L;

    private final FileRepository fileRepository;
    private final FileAccessDecisionService fileAccessDecisionService;
    private final FileViewTicketStore fileViewTicketStore;
    private final ObjectStorageProperties objectStorageProperties;
    private final TicketIdGenerator ticketIdGenerator;

    public FileViewTicketService(
        FileRepository fileRepository,
        FileAccessDecisionService fileAccessDecisionService,
        FileViewTicketStore fileViewTicketStore,
        ObjectStorageProperties objectStorageProperties,
        TicketIdGenerator ticketIdGenerator
    ) {
        this.fileRepository = fileRepository;
        this.fileAccessDecisionService = fileAccessDecisionService;
        this.fileViewTicketStore = fileViewTicketStore;
        this.objectStorageProperties = objectStorageProperties;
        this.ticketIdGenerator = ticketIdGenerator;
    }

    public FileViewTicketResponse issue(Long fileId, String variantParam) {
        return issueInternal(fileId, variantParam);
    }

    public FileViewTicketBatchResponse issueBatch(List<FileViewTicketBatchItemRequest> items) {
        if (items == null || items.isEmpty()) {
            throw new ApiException(ErrorCode.COMMON_BAD_REQUEST);
        }
        int batchMax = objectStorageProperties.getViewTicketBatchMaxItems();
        if (batchMax <= 0) {
            batchMax = 100;
        }
        if (items.size() > batchMax) {
            throw new ApiException(ErrorCode.COMMON_BAD_REQUEST);
        }

        Map<String, FileViewTicketBatchItemResponse> resolvedByKey = new LinkedHashMap<>();
        List<FileViewTicketBatchItemResponse> responses = new ArrayList<>(items.size());

        for (FileViewTicketBatchItemRequest item : items) {
            String cacheKey = batchCacheKey(item.fileId(), item.variant());
            FileViewTicketBatchItemResponse cached = resolvedByKey.get(cacheKey);
            if (cached == null) {
                cached = issueBatchItem(item);
                resolvedByKey.put(cacheKey, cached);
            }
            responses.add(copyBatchItemForRequest(item, cached));
        }
        return new FileViewTicketBatchResponse(responses);
    }

    private FileViewTicketBatchItemResponse issueBatchItem(FileViewTicketBatchItemRequest item) {
        String normalizedVariant = normalizeVariantParam(item.variant());
        try {
            FileViewTicketResponse ticket = issueInternal(item.fileId(), item.variant());
            return new FileViewTicketBatchItemResponse(
                item.fileId(),
                normalizedVariant,
                true,
                ticket.viewUrl(),
                ticket.expiresInSec(),
                ticket.protectedFile(),
                null
            );
        } catch (ApiException ex) {
            return new FileViewTicketBatchItemResponse(
                item.fileId(),
                normalizedVariant,
                false,
                null,
                0L,
                false,
                ex.getErrorCode().getCode()
            );
        }
    }

    private FileViewTicketBatchItemResponse copyBatchItemForRequest(
        FileViewTicketBatchItemRequest item,
        FileViewTicketBatchItemResponse cached
    ) {
        return new FileViewTicketBatchItemResponse(
            item.fileId(),
            normalizeVariantParam(item.variant()),
            cached.success(),
            cached.viewUrl(),
            cached.expiresInSec(),
            cached.protectedFile(),
            cached.errorCode()
        );
    }

    private FileViewTicketResponse issueInternal(Long fileId, String variantParam) {
        FileEntity file = fileRepository.findByIdAndDeletedAtIsNull(fileId)
            .orElseThrow(() -> new ApiException(ErrorCode.FILE_NOT_FOUND));

        FileAccessDecision accessDecision = fileAccessDecisionService.decide(file);
        if (!accessDecision.allowed()) {
            throw new ApiException(ErrorCode.FILE_NOT_FOUND);
        }

        if (accessDecision.deliveryMode() == FileDeliveryMode.PUBLIC) {
            return new FileViewTicketResponse(buildViewUrl(fileId, variantParam, null), 0L, false);
        }

        Duration ticketTtl = resolveTicketTtl();
        String ticket = buildTicket();
        fileViewTicketStore.save(ticket, fileId, ticketTtl);
        return new FileViewTicketResponse(buildViewUrl(fileId, variantParam, ticket), ticketTtl.toSeconds(), true);
    }

    private String batchCacheKey(Long fileId, String variantParam) {
        return fileId + "|" + normalizeVariantParam(variantParam);
    }

    private String normalizeVariantParam(String variantParam) {
        if (!StringUtils.hasText(variantParam)) {
            return null;
        }
        return variantParam.trim();
    }

    public Duration validate(Long fileId, String ticket) {
        if (ticket == null || ticket.isBlank()) {
            throw new ApiException(ErrorCode.FILE_NOT_FOUND);
        }

        Optional<FileViewTicketStore.FileViewTicketState> ticketState = fileViewTicketStore.find(ticket);
        if (ticketState.isEmpty()) {
            throw new ApiException(ErrorCode.FILE_NOT_FOUND);
        }

        FileViewTicketStore.FileViewTicketState state = ticketState.get();
        if (!state.fileId().equals(fileId) || state.remainingTtl().isZero() || state.remainingTtl().isNegative()) {
            throw new ApiException(ErrorCode.FILE_NOT_FOUND);
        }
        return state.remainingTtl();
    }

    private Duration resolveTicketTtl() {
        long protectedViewExpireSeconds = objectStorageProperties.getProtectedViewExpireSeconds();
        if (protectedViewExpireSeconds <= 0L) {
            protectedViewExpireSeconds = DEFAULT_PROTECTED_VIEW_EXPIRE_SECONDS;
        }
        if (protectedViewExpireSeconds > MAX_TICKET_TTL_SECONDS) {
            protectedViewExpireSeconds = MAX_TICKET_TTL_SECONDS;
        }
        return Duration.ofSeconds(protectedViewExpireSeconds);
    }

    private String buildTicket() {
        return ticketIdGenerator.generate(TICKET_PREFIX);
    }

    private String buildViewUrl(Long fileId, String variantParam, String ticket) {
        StringBuilder builder = new StringBuilder("/api/files/")
            .append(fileId)
            .append("/view");

        boolean hasQuery = false;
        if (variantParam != null && !variantParam.isBlank()) {
            builder.append("?variant=").append(variantParam);
            hasQuery = true;
        }
        if (ticket != null && !ticket.isBlank()) {
            builder.append(hasQuery ? '&' : '?')
                .append("ticket=")
                .append(ticket);
        }
        return builder.toString();
    }
}
