package com.mocktalkback.domain.file.upload.service;

import com.mocktalkback.global.common.dto.ErrorCode;
import com.mocktalkback.global.i18n.ApiException;
import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.mocktalkback.domain.file.upload.dto.UploadInitContext;
import com.mocktalkback.domain.file.upload.type.UploadPurpose;

@Component
public class UploadPolicyValidator {

    private static final long MAX_UPLOAD_SIZE = 50L * 1024L * 1024L;

    private static final Set<String> ALLOWED_ATTACHMENT_EXTENSIONS = Set.of(
        "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "hwp", "hwpx",
        "txt", "csv", "zip", "7z", "rar",
        "jpg", "jpeg", "png", "webp", "gif",
        "mp4", "webm", "mp3", "wav"
    );

    private static final Set<String> ALLOWED_ATTACHMENT_MIME_TYPES = Set.of(
        "application/pdf",
        "application/msword",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "application/vnd.ms-excel",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "application/vnd.ms-powerpoint",
        "application/vnd.openxmlformats-officedocument.presentationml.presentation",
        "application/x-hwp",
        "application/vnd.hancom.hwp",
        "application/haansofthwp",
        "application/haansofthwpx",
        "application/vnd.hancom.hwpx",
        "text/plain",
        "text/csv",
        "application/csv",
        "application/zip",
        "application/x-zip-compressed",
        "application/x-7z-compressed",
        "application/x-rar-compressed",
        "application/vnd.rar",
        "image/jpg",
        "image/jpeg",
        "image/png",
        "image/webp",
        "image/gif",
        "video/mp4",
        "video/webm",
        "audio/mpeg",
        "audio/wav",
        "audio/x-wav"
    );

    private static final Set<String> BLOCKED_ATTACHMENT_EXTENSIONS = Set.of(
        "ade", "adp", "apk", "appx", "bat", "cmd", "com", "cpl", "dll", "exe", "hta",
        "ins", "isp", "jar", "js", "jse", "lnk", "msc", "msi", "msp", "mst", "pif",
        "ps1", "reg", "scr", "sh", "vb", "vbe", "vbs", "ws", "wsc", "wsf", "wsh"
    );

    private static final Set<String> BLOCKED_ATTACHMENT_MIME_TYPES = Set.of(
        "application/x-msdownload",
        "application/x-msdos-program",
        "application/x-dosexec",
        "application/x-sh",
        "application/x-bat",
        "application/x-shellscript"
    );

    public void validateInit(
        UploadPurpose purpose,
        String originalFileName,
        String contentType,
        long fileSize,
        UploadInitContext context
    ) {
        if (purpose == null) {
            throw new ApiException(ErrorCode.UPLOAD_PURPOSE_EMPTY);
        }
        if (!StringUtils.hasText(originalFileName)) {
            throw new ApiException(ErrorCode.UPLOAD_FILENAME_EMPTY);
        }
        if (!StringUtils.hasText(contentType)) {
            throw new ApiException(ErrorCode.UPLOAD_CONTENT_TYPE_EMPTY);
        }
        if (fileSize <= 0L) {
            throw new ApiException(ErrorCode.UPLOAD_FILE_SIZE_INVALID);
        }
        if (fileSize > MAX_UPLOAD_SIZE) {
            throw new ApiException(ErrorCode.UPLOAD_FILE_SIZE_MAX);
        }

        String normalizedMimeType = normalizeMimeType(contentType);
        if (purpose == UploadPurpose.EDITOR_IMAGE) {
            validateImageMimeType(normalizedMimeType);
            return;
        }
        if (purpose == UploadPurpose.EDITOR_VIDEO) {
            validateEditorVideoMimeType(normalizedMimeType);
            return;
        }
        if (purpose == UploadPurpose.ARTICLE_ATTACHMENT) {
            validateAttachment(originalFileName, normalizedMimeType);
            return;
        }
        if (purpose == UploadPurpose.BOARD_IMAGE) {
            validateBoardContext(context);
            validateImageMimeType(normalizedMimeType);
            return;
        }
        validateImageMimeType(normalizedMimeType);
    }

    public String normalizeMimeType(String contentType) {
        String normalized = contentType.trim().toLowerCase(Locale.ROOT);
        int semicolonIndex = normalized.indexOf(';');
        if (semicolonIndex >= 0) {
            normalized = normalized.substring(0, semicolonIndex).trim();
        }
        return normalized;
    }

    private void validateBoardContext(UploadInitContext context) {
        if (context == null) {
            throw new ApiException(ErrorCode.UPLOAD_BOARD_CONTEXT_EMPTY);
        }
        if (context.boardId() == null || context.boardId() <= 0L) {
            throw new ApiException(ErrorCode.UPLOAD_BOARD_ID_INVALID);
        }
        if (context.channel() == null) {
            throw new ApiException(ErrorCode.UPLOAD_BOARD_CHANNEL_EMPTY);
        }
    }

    private void validateAttachment(String originalFileName, String normalizedMimeType) {
        String extension = normalizeExtension(resolveExtension(originalFileName));
        if (!StringUtils.hasText(extension)) {
            throw new ApiException(ErrorCode.UPLOAD_FILE_TYPE_NOT_ALLOWED);
        }
        if (BLOCKED_ATTACHMENT_EXTENSIONS.contains(extension)) {
            throw new ApiException(ErrorCode.UPLOAD_EXTENSION_BLOCKED);
        }
        if (!ALLOWED_ATTACHMENT_EXTENSIONS.contains(extension)) {
            throw new ApiException(ErrorCode.UPLOAD_FILE_TYPE_NOT_ALLOWED);
        }

        if (BLOCKED_ATTACHMENT_MIME_TYPES.contains(normalizedMimeType)) {
            throw new ApiException(ErrorCode.UPLOAD_FILE_TYPE_BLOCKED);
        }
        if ("application/octet-stream".equals(normalizedMimeType)) {
            return;
        }
        if (!ALLOWED_ATTACHMENT_MIME_TYPES.contains(normalizedMimeType)) {
            throw new ApiException(ErrorCode.UPLOAD_FILE_TYPE_NOT_ALLOWED);
        }
    }

    private void validateImageMimeType(String normalizedMimeType) {
        if (!normalizedMimeType.startsWith("image/")) {
            throw new ApiException(ErrorCode.UPLOAD_IMAGE_ONLY);
        }
    }

    private void validateEditorVideoMimeType(String normalizedMimeType) {
        if (!"video/mp4".equals(normalizedMimeType)
            && !"video/webm".equals(normalizedMimeType)
            && !"video/ogg".equals(normalizedMimeType)) {
            throw new ApiException(ErrorCode.UPLOAD_VIDEO_FORMAT_INVALID);
        }
    }

    private String resolveExtension(String originalFilename) {
        if (!StringUtils.hasText(originalFilename)) {
            return null;
        }
        String normalized = originalFilename.trim();
        int dotIndex = normalized.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == normalized.length() - 1) {
            return null;
        }
        return normalized.substring(dotIndex + 1);
    }

    private String normalizeExtension(String extension) {
        if (!StringUtils.hasText(extension)) {
            return null;
        }
        return extension.trim().toLowerCase(Locale.ROOT);
    }
}
