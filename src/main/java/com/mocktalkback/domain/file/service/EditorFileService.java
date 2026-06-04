package com.mocktalkback.domain.file.service;

import com.mocktalkback.global.common.dto.ErrorCode;
import com.mocktalkback.global.i18n.ApiException;
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
public class EditorFileService {

    private static final long MAX_UPLOAD_SIZE = 50L * 1024L * 1024L;

    private final FileRepository fileRepository;
    private final FileClassRepository fileClassRepository;
    private final FileMapper fileMapper;
    private final ImageOptimizationService imageOptimizationService;
    private final TemporaryFilePolicy temporaryFilePolicy;

    @Transactional
    public FileResponse completeEditorFileUpload(StoredFile storedFile, boolean preserveMetadata) {
        validateStoredFile(storedFile);
        String fileClassCode = resolveFileClassCode(storedFile.mimeType());

        ImageOptimizationService.OriginalFileResult processed = imageOptimizationService
            .processOriginal(storedFile, preserveMetadata);
        FileClassEntity fileClass = getOrCreateFileClass(fileClassCode);

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
        String contentType = storedFile.mimeType();
        if (!StringUtils.hasText(contentType)) {
            throw new ApiException(ErrorCode.EDITOR_FILE_UNSUPPORTED);
        }
        if (contentType.startsWith("image/")) {
            return;
        }
        if ("video/mp4".equals(contentType) || "video/webm".equals(contentType) || "video/ogg".equals(contentType)) {
            return;
        }
        throw new ApiException(ErrorCode.EDITOR_MEDIA_TYPE_INVALID);
    }

    private String resolveFileClassCode(String contentType) {
        if (StringUtils.hasText(contentType) && contentType.startsWith("image/")) {
            return FileClassCode.ARTICLE_CONTENT_IMAGE;
        }
        return FileClassCode.ARTICLE_CONTENT_VIDEO;
    }

    private FileClassEntity getOrCreateFileClass(String fileClassCode) {
        return fileClassRepository.findByCode(fileClassCode)
            .orElseGet(() -> fileClassRepository.save(createFileClass(fileClassCode)));
    }

    private FileClassEntity createFileClass(String fileClassCode) {
        if (FileClassCode.ARTICLE_CONTENT_IMAGE.equals(fileClassCode)) {
            return FileClassEntity.builder()
                .code(fileClassCode)
                .name("게시글 본문 이미지")
                .description("에디터 본문에 삽입되는 이미지")
                .mediaKind(MediaKind.IMAGE)
                .build();
        }
        return FileClassEntity.builder()
            .code(fileClassCode)
            .name("게시글 본문 영상")
            .description("에디터 본문에 삽입되는 영상")
            .mediaKind(MediaKind.VIDEO)
            .build();
    }
}
