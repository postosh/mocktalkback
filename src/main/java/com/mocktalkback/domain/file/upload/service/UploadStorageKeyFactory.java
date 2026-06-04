package com.mocktalkback.domain.file.upload.service;

import com.mocktalkback.global.common.dto.ErrorCode;
import com.mocktalkback.global.i18n.ApiException;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.mocktalkback.domain.file.service.FileStoragePathResolver;

@Component
public class UploadStorageKeyFactory {

    private final FileStoragePathResolver pathResolver;
    private final String keyPrefix;

    public UploadStorageKeyFactory(
        FileStoragePathResolver pathResolver,
        @Value("${app.object-storage.key-prefix:uploads}") String keyPrefix
    ) {
        this.pathResolver = pathResolver;
        this.keyPrefix = keyPrefix;
    }

    public PreparedUploadFile prepare(String fileClassCode, Long ownerId, String originalFileName) {
        if (!StringUtils.hasText(fileClassCode)) {
            throw new ApiException(ErrorCode.FILE_CLASS_CODE_EMPTY);
        }
        if (ownerId == null) {
            throw new ApiException(ErrorCode.FILE_OWNER_EMPTY);
        }
        String resolvedOriginalName = resolveOriginalFileName(originalFileName);
        String sanitizedOriginalName = sanitizeFileNameForStorage(resolvedOriginalName);
        String savedName = resolveStoredFileName(sanitizedOriginalName);
        String fileNameForDatabase = resolveFileNameForDatabase(resolvedOriginalName);
        String category = pathResolver.resolveCategory(fileClassCode);
        String storageKey = buildStorageKey(category, ownerId, savedName);
        return new PreparedUploadFile(fileNameForDatabase, storageKey);
    }

    private String resolveOriginalFileName(String originalFileName) {
        String original = Objects.requireNonNullElse(originalFileName, "file");
        String cleanedPath = StringUtils.cleanPath(original).replace('\\', '/');
        int slashIndex = cleanedPath.lastIndexOf('/');
        String fileName = slashIndex >= 0 ? cleanedPath.substring(slashIndex + 1) : cleanedPath;
        if (!StringUtils.hasText(fileName)) {
            return "file";
        }
        return fileName;
    }

    private String sanitizeFileNameForStorage(String originalName) {
        String sanitized = originalName.replaceAll("[^a-zA-Z0-9._-]", "_");
        if (!StringUtils.hasText(sanitized)) {
            return "file";
        }
        return sanitized;
    }

    private String resolveStoredFileName(String sanitizedOriginalName) {
        String uuid = UUID.randomUUID().toString().replace("-", "");
        String extension = resolveExtension(sanitizedOriginalName);
        if (!StringUtils.hasText(extension)) {
            return uuid;
        }
        return uuid + "." + extension;
    }

    private String resolveFileNameForDatabase(String originalName) {
        return originalName;
    }

    private String resolveExtension(String fileName) {
        if (!StringUtils.hasText(fileName)) {
            return null;
        }
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == fileName.length() - 1) {
            return null;
        }
        return fileName.substring(dotIndex + 1);
    }

    private String buildStorageKey(String category, Long ownerId, String savedName) {
        LocalDate today = LocalDate.now();
        String year = String.valueOf(today.getYear());
        String month = String.format("%02d", today.getMonthValue());
        String day = String.format("%02d", today.getDayOfMonth());
        String normalizedPrefix = normalizePrefix(keyPrefix);
        return normalizedPrefix + "/" + category + "/" + ownerId + "/" + year + "/" + month + "/" + day + "/" + savedName;
    }

    private String normalizePrefix(String rawPrefix) {
        if (!StringUtils.hasText(rawPrefix)) {
            return "uploads";
        }
        String normalized = rawPrefix.trim().replace('\\', '/');
        normalized = normalized.replaceAll("^/+", "");
        normalized = normalized.replaceAll("/+$", "");
        if (!StringUtils.hasText(normalized)) {
            return "uploads";
        }
        return normalized;
    }

    public record PreparedUploadFile(
        String fileNameForDatabase,
        String storageKey
    ) {
    }
}
