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
class EditorFileServiceTest {

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

    private EditorFileService editorFileService;

    @BeforeEach
    void setUp() {
        editorFileService = new EditorFileService(
            fileRepository,
            fileClassRepository,
            fileMapper,
            imageOptimizationService,
            temporaryFilePolicy
        );
    }

    // completeEditorFileUpload는 정상 이미지 입력 시 파일 응답을 반환해야 한다.
    @Test
    void completeEditorFileUpload_returns_file_response() {
        // given
        FileStorage.StoredFile storedFile = new FileStorage.StoredFile(
            "image.png",
            "uploads/article_content_image/1/2026/03/05/image.png",
            120L,
            "image/png"
        );
        FileClassEntity fileClass = FileClassEntity.builder()
            .code(FileClassCode.ARTICLE_CONTENT_IMAGE)
            .name("게시글 본문 이미지")
            .description("테스트")
            .mediaKind(MediaKind.IMAGE)
            .build();
        FileResponse expected = new FileResponse(
            1L,
            1L,
            "image.png",
            storedFile.storageKey(),
            120L,
            "image/png",
            Instant.now(),
            Instant.now(),
            null
        );
        when(fileClassRepository.findByCode(FileClassCode.ARTICLE_CONTENT_IMAGE)).thenReturn(Optional.of(fileClass));
        when(imageOptimizationService.processOriginal(storedFile, false))
            .thenReturn(new ImageOptimizationService.OriginalFileResult(120L, "image/png", false));
        when(temporaryFilePolicy.resolveExpiry()).thenReturn(Instant.now().plusSeconds(3600));
        when(fileRepository.save(any(FileEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(fileMapper.toResponse(any(FileEntity.class))).thenReturn(expected);

        // when
        FileResponse result = editorFileService.completeEditorFileUpload(storedFile, false);

        // then
        assertThat(result).isEqualTo(expected);
        verify(imageOptimizationService).enqueueVariantGeneration(any(FileEntity.class));
    }

    // completeEditorFileUpload는 비허용 MIME이면 예외를 발생시켜야 한다.
    @Test
    void completeEditorFileUpload_throws_when_unsupported_mime() {
        // given
        FileStorage.StoredFile storedFile = new FileStorage.StoredFile(
            "doc.pdf",
            "uploads/article_content_image/1/2026/03/05/doc.pdf",
            120L,
            "application/pdf"
        );

        // when & then
        assertThatThrownBy(() -> editorFileService.completeEditorFileUpload(storedFile, false))
            .isInstanceOf(ApiException.class)
            .extracting(ex -> ((ApiException) ex).getErrorCode())
            .isEqualTo(ErrorCode.EDITOR_MEDIA_TYPE_INVALID);
    }
}
