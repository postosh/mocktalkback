package com.mocktalkback.domain.file.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.mocktalkback.global.common.dto.ErrorCode;
import com.mocktalkback.global.i18n.ApiException;
import com.mocktalkback.domain.file.dto.FileResponse;
import com.mocktalkback.domain.file.entity.FileClassEntity;
import com.mocktalkback.domain.file.entity.FileEntity;
import com.mocktalkback.domain.file.mapper.FileMapper;
import com.mocktalkback.domain.file.repository.FileClassRepository;
import com.mocktalkback.domain.file.repository.FileRepository;
import com.mocktalkback.domain.file.type.FileClassCode;
import com.mocktalkback.domain.file.type.MediaKind;

@ExtendWith(MockitoExtension.class)
class ArticleAttachmentFileServiceTest {

    @Mock
    private FileRepository fileRepository;

    @Mock
    private FileClassRepository fileClassRepository;

    @Mock
    private FileMapper fileMapper;

    @Mock
    private ImageOptimizationService imageOptimizationService;

    @Mock
    private TemporaryFilePolicy temporaryFilePolicy;

    private ArticleAttachmentFileService articleAttachmentFileService;

    @BeforeEach
    void setUp() {
        articleAttachmentFileService = new ArticleAttachmentFileService(
            fileRepository,
            fileClassRepository,
            fileMapper,
            imageOptimizationService,
            temporaryFilePolicy
        );
    }

    // completeArticleAttachmentFileUpload는 정상 첨부 입력 시 파일 응답을 반환해야 한다.
    @Test
    void completeArticleAttachmentFileUpload_returns_file_response() {
        // given
        FileStorage.StoredFile storedFile = new FileStorage.StoredFile(
            "manual.pdf",
            "uploads/article_attachment/1/2026/03/05/manual.pdf",
            2048L,
            "application/pdf"
        );
        FileClassEntity fileClass = FileClassEntity.builder()
            .code(FileClassCode.ARTICLE_ATTACHMENT)
            .name("게시글 첨부파일")
            .description("테스트")
            .mediaKind(MediaKind.ANY)
            .build();
        FileResponse expected = new FileResponse(
            10L,
            1L,
            "manual.pdf",
            storedFile.storageKey(),
            2048L,
            "application/pdf",
            Instant.now(),
            Instant.now(),
            null
        );
        when(fileClassRepository.findByCode(FileClassCode.ARTICLE_ATTACHMENT)).thenReturn(Optional.of(fileClass));
        when(imageOptimizationService.processOriginal(storedFile, true))
            .thenReturn(new ImageOptimizationService.OriginalFileResult(2048L, "application/pdf", true));
        when(temporaryFilePolicy.resolveExpiry()).thenReturn(Instant.now().plusSeconds(3600));
        when(fileRepository.save(any(FileEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(fileMapper.toResponse(any(FileEntity.class))).thenReturn(expected);

        // when
        FileResponse result = articleAttachmentFileService.completeArticleAttachmentFileUpload(storedFile, false);

        // then
        assertThat(result).isEqualTo(expected);
        verify(imageOptimizationService).enqueueVariantGeneration(any(FileEntity.class));
    }

    // completeArticleAttachmentFileUpload는 금지 확장자면 예외를 발생시켜야 한다.
    @Test
    void completeArticleAttachmentFileUpload_throws_when_blocked_extension() {
        // given
        FileStorage.StoredFile storedFile = new FileStorage.StoredFile(
            "malware.exe",
            "uploads/article_attachment/1/2026/03/05/malware.exe",
            120L,
            "application/octet-stream"
        );

        // when & then
        assertThatThrownBy(() -> articleAttachmentFileService.completeArticleAttachmentFileUpload(storedFile, false))
            .isInstanceOf(ApiException.class)
            .extracting(ex -> ((ApiException) ex).getErrorCode())
            .isEqualTo(ErrorCode.UPLOAD_EXTENSION_BLOCKED);
    }
}
