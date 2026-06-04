package com.mocktalkback.domain.file.service;

import com.mocktalkback.global.common.dto.ErrorCode;
import com.mocktalkback.global.i18n.ApiException;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.mocktalkback.domain.file.dto.FileResponse;
import com.mocktalkback.domain.file.entity.FileClassEntity;
import com.mocktalkback.domain.file.entity.FileEntity;
import com.mocktalkback.domain.file.mapper.FileMapper;
import com.mocktalkback.domain.file.repository.FileClassRepository;
import com.mocktalkback.domain.file.repository.FileRepository;
import com.mocktalkback.domain.file.service.FileStorage.StoredFile;
import com.mocktalkback.domain.file.type.FileClassCode;
import com.mocktalkback.domain.file.type.MediaKind;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ArticleAttachmentFileService {

    private static final long MAX_UPLOAD_SIZE = 50L * 1024L * 1024L;

    // 운영에서 허용할 첨부파일 확장자 목록(소문자 기준)
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
        "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "hwp", "hwpx",
        "txt", "csv", "zip", "7z", "rar",
        "jpg", "jpeg", "png", "webp", "gif",
        "mp4", "webm", "mp3", "wav"
    );

    // MIME은 허용 목록 기반으로 검증한다.
    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
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

    private static final Set<String> BLOCKED_EXTENSIONS = Set.of(
        "ade", "adp", "apk", "appx", "bat", "cmd", "com", "cpl", "dll", "exe", "hta",
        "ins", "isp", "jar", "js", "jse", "lnk", "msc", "msi", "msp", "mst", "pif",
        "ps1", "reg", "scr", "sh", "vb", "vbe", "vbs", "ws", "wsc", "wsf", "wsh"
    );

    private static final Set<String> BLOCKED_MIME_TYPES = Set.of(
        "application/x-msdownload",
        "application/x-msdos-program",
        "application/x-dosexec",
        "application/x-sh",
        "application/x-bat",
        "application/x-shellscript"
    );

    private final FileRepository fileRepository;
    private final FileClassRepository fileClassRepository;
    private final FileMapper fileMapper;
    private final ImageOptimizationService imageOptimizationService;
    private final TemporaryFilePolicy temporaryFilePolicy;

    @Transactional
    public FileResponse completeArticleAttachmentFileUpload(StoredFile storedFile, boolean preserveMetadata) {
        validateStoredFile(storedFile);

        // 첨부파일은 원본 보존을 우선한다(파일명/바이트 원본 유지).
        boolean preserveOriginalAttachment = true;
        ImageOptimizationService.OriginalFileResult processed = imageOptimizationService
            .processOriginal(storedFile, preserveOriginalAttachment);
        FileClassEntity fileClass = getOrCreateFileClass();

        FileEntity fileEntity = FileEntity.builder()
            .fileClass(fileClass)
            .fileName(storedFile.fileName())
            .storageKey(storedFile.storageKey())
            .fileSize(processed.fileSize())
            .mimeType(processed.mimeType())
            .metadataPreserved(processed.metadataPreserved())
            .tempExpiresAt(temporaryFilePolicy.resolveExpiry())
            .build();

        FileEntity saved = fileRepository.save(fileEntity);
        imageOptimizationService.enqueueVariantGeneration(saved);
        return fileMapper.toResponse(saved);
    }

    private void validateStoredFile(StoredFile storedFile) {
        if (storedFile == null) {
            throw new ApiException(ErrorCode.UPLOAD_FILE_INFO_MISSING);
        }
        if (storedFile.fileSize() == null || storedFile.fileSize() <= 0L) {
            throw new ApiException(ErrorCode.UPLOAD_FILE_SIZE_INVALID);
        }
        if (storedFile.fileSize() > MAX_UPLOAD_SIZE) {
            throw new ApiException(ErrorCode.UPLOAD_FILE_SIZE_MAX);
        }

        String fileName = storedFile.fileName();
        String extension = normalizeExtension(resolveExtension(fileName));
        if (!StringUtils.hasText(extension)) {
            throw new ApiException(ErrorCode.UPLOAD_FILE_TYPE_NOT_ALLOWED);
        }
        if (BLOCKED_EXTENSIONS.contains(extension)) {
            throw new ApiException(ErrorCode.UPLOAD_EXTENSION_BLOCKED);
        }
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new ApiException(ErrorCode.UPLOAD_FILE_TYPE_NOT_ALLOWED);
        }

        String contentType = storedFile.mimeType();
        if (!StringUtils.hasText(contentType)) {
            throw new ApiException(ErrorCode.UPLOAD_FILE_TYPE_NOT_ALLOWED);
        }
        String normalizedContentType = normalizeMimeType(contentType);
        if (BLOCKED_MIME_TYPES.contains(normalizedContentType)) {
            throw new ApiException(ErrorCode.UPLOAD_FILE_TYPE_BLOCKED);
        }
        if ("application/octet-stream".equals(normalizedContentType)) {
            return;
        }
        if (!ALLOWED_MIME_TYPES.contains(normalizedContentType)) {
            throw new ApiException(ErrorCode.UPLOAD_FILE_TYPE_NOT_ALLOWED);
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

    private String normalizeMimeType(String contentType) {
        String normalized = contentType.trim().toLowerCase(Locale.ROOT);
        int semicolonIndex = normalized.indexOf(';');
        if (semicolonIndex >= 0) {
            normalized = normalized.substring(0, semicolonIndex).trim();
        }
        return normalized;
    }

    private FileClassEntity getOrCreateFileClass() {
        Optional<FileClassEntity> optional = fileClassRepository.findByCode(FileClassCode.ARTICLE_ATTACHMENT);
        if (optional.isPresent()) {
            return optional.get();
        }
        FileClassEntity created = FileClassEntity.builder()
            .code(FileClassCode.ARTICLE_ATTACHMENT)
            .name("게시글 첨부파일")
            .description("게시글에 첨부되는 일반 파일")
            .mediaKind(MediaKind.ANY)
            .build();
        return fileClassRepository.save(created);
    }
}
