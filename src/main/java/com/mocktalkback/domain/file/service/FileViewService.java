package com.mocktalkback.domain.file.service;

import com.mocktalkback.global.common.dto.ErrorCode;
import com.mocktalkback.global.i18n.ApiException;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.mocktalkback.domain.file.entity.FileEntity;
import com.mocktalkback.domain.file.entity.FileVariantEntity;
import com.mocktalkback.domain.file.repository.FileRepository;
import com.mocktalkback.domain.file.repository.FileVariantRepository;
import com.mocktalkback.domain.file.type.FileVariantCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class FileViewService {

    private final FileRepository fileRepository;
    private final FileVariantRepository fileVariantRepository;
    private final FileStorage fileStorage;
    private final FileAccessDecisionService fileAccessDecisionService;
    private final FileViewTicketService fileViewTicketService;

    public String resolveViewLocation(Long fileId, String variantParam) {
        return resolveViewLocation(fileId, variantParam, null);
    }

    public String resolveViewLocation(Long fileId, String variantParam, String ticket) {
        FileEntity file = fileRepository.findByIdAndDeletedAtIsNull(fileId)
            .orElseThrow(() -> new ApiException(ErrorCode.FILE_NOT_FOUND));

        FileDeliveryMode deliveryMode = fileAccessDecisionService.resolveDeliveryMode(file);
        if (deliveryMode == null) {
            throw new ApiException(ErrorCode.FILE_NOT_FOUND);
        }

        Duration ticketRemainingTtl = null;
        if (deliveryMode == FileDeliveryMode.PROTECTED) {
            ticketRemainingTtl = fileViewTicketService.validate(fileId, ticket);
        }

        FileVariantCode variantCode = resolveVariantCode(variantParam);
        if (variantCode == null || !isImage(file.getMimeType())) {
            return resolveDeliveryUrl(file.getStorageKey(), deliveryMode, ticketRemainingTtl);
        }

        Optional<FileVariantEntity> variant = fileVariantRepository
            .findByFileIdAndVariantCodeAndDeletedAtIsNull(fileId, variantCode);
        if (variant.isPresent()) {
            return resolveDeliveryUrl(variant.get().getStorageKey(), deliveryMode, ticketRemainingTtl);
        }
        return resolveDeliveryUrl(file.getStorageKey(), deliveryMode, ticketRemainingTtl);
    }

    private FileVariantCode resolveVariantCode(String variantParam) {
        if (variantParam == null || variantParam.isBlank()) {
            return FileVariantCode.MEDIUM;
        }
        String normalized = variantParam.trim().toLowerCase(Locale.ROOT);
        if ("original".equals(normalized)) {
            return null;
        }
        try {
            return FileVariantCode.valueOf(normalized.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new ApiException(ErrorCode.FILE_VARIANT_UNSUPPORTED);
        }
    }

    private boolean isImage(String mimeType) {
        return mimeType != null && mimeType.startsWith("image/");
    }

    private String resolveDeliveryUrl(String storageKey, FileDeliveryMode deliveryMode, Duration ticketRemainingTtl) {
        if (deliveryMode == FileDeliveryMode.PROTECTED) {
            return fileStorage.resolveProtectedViewUrl(storageKey, ticketRemainingTtl);
        }
        return fileStorage.resolveViewUrl(storageKey);
    }

}
