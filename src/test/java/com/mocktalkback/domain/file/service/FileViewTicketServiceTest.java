package com.mocktalkback.domain.file.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import org.springframework.test.util.ReflectionTestUtils;
import com.mocktalkback.global.common.dto.ErrorCode;
import com.mocktalkback.global.i18n.ApiException;

import com.mocktalkback.domain.file.dto.FileViewTicketBatchItemRequest;
import com.mocktalkback.domain.file.dto.FileViewTicketBatchItemResponse;
import com.mocktalkback.domain.file.dto.FileViewTicketBatchResponse;
import com.mocktalkback.domain.file.dto.FileViewTicketResponse;
import com.mocktalkback.domain.file.entity.FileClassEntity;
import com.mocktalkback.domain.file.entity.FileEntity;
import com.mocktalkback.domain.file.repository.FileRepository;
import com.mocktalkback.domain.file.type.FileClassCode;
import com.mocktalkback.domain.file.type.MediaKind;
import com.mocktalkback.global.auth.ticket.TicketIdGenerator;
import com.mocktalkback.infra.storage.ObjectStorageProperties;

class FileViewTicketServiceTest {

    // 보호 파일 ticket 발급은 보호 조회 TTL 기준으로 재사용 가능한 ticket을 저장해야 한다.
    @Test
    void issue_saves_ticket_for_protected_file() {
        // given: 보호 파일과 ticket 저장소가 있다.
        FileRepository fileRepository = mock(FileRepository.class);
        FileAccessDecisionService accessDecisionService = mock(FileAccessDecisionService.class);
        FileViewTicketStore fileViewTicketStore = mock(FileViewTicketStore.class);
        TicketIdGenerator ticketIdGenerator = mock(TicketIdGenerator.class);
        ObjectStorageProperties properties = new ObjectStorageProperties();
        properties.setProtectedViewExpireSeconds(120L);
        FileViewTicketService service = new FileViewTicketService(
            fileRepository,
            accessDecisionService,
            fileViewTicketStore,
            properties,
            ticketIdGenerator
        );
        FileEntity file = createFileEntity(31L, FileClassCode.ARTICLE_CONTENT_IMAGE);

        when(fileRepository.findByIdAndDeletedAtIsNull(31L)).thenReturn(Optional.of(file));
        when(accessDecisionService.decide(file)).thenReturn(FileAccessDecision.protectedAccess());
        when(ticketIdGenerator.generate("fv_")).thenReturn("fv_ticket_test");

        // when: 보호 파일 보기 ticket을 발급하면
        FileViewTicketResponse response = service.issue(31L, "medium");

        // then: ticket이 저장되고 ticket 포함 viewUrl을 반환한다.
        assertThat(response.protectedFile()).isTrue();
        assertThat(response.expiresInSec()).isEqualTo(120L);
        assertThat(response.viewUrl()).startsWith("/api/files/31/view?");
        assertThat(response.viewUrl()).contains("variant=medium");
        assertThat(response.viewUrl()).contains("ticket=fv_ticket_test");
        verify(fileViewTicketStore).save(eq("fv_ticket_test"), eq(31L), eq(Duration.ofSeconds(120L)));
    }

    // 공개 파일 ticket 발급은 Redis 저장 없이 기존 보기 URL을 반환해야 한다.
    @Test
    void issue_returns_public_view_url_without_ticket_for_public_file() {
        // given: 공개 전달 모드의 게시판 이미지 파일이 있다.
        FileRepository fileRepository = mock(FileRepository.class);
        FileAccessDecisionService accessDecisionService = mock(FileAccessDecisionService.class);
        FileViewTicketStore fileViewTicketStore = mock(FileViewTicketStore.class);
        TicketIdGenerator ticketIdGenerator = mock(TicketIdGenerator.class);
        ObjectStorageProperties properties = new ObjectStorageProperties();
        FileViewTicketService service = new FileViewTicketService(
            fileRepository,
            accessDecisionService,
            fileViewTicketStore,
            properties,
            ticketIdGenerator
        );
        FileEntity file = createFileEntity(44L, FileClassCode.BOARD_IMAGE);

        when(fileRepository.findByIdAndDeletedAtIsNull(44L)).thenReturn(Optional.of(file));
        when(accessDecisionService.decide(file)).thenReturn(FileAccessDecision.publicAccess());

        // when: 공개 파일 보기 ticket을 발급하면
        FileViewTicketResponse response = service.issue(44L, "thumb");

        // then: ticket 없이 기존 보기 URL을 반환한다.
        assertThat(response.protectedFile()).isFalse();
        assertThat(response.expiresInSec()).isZero();
        assertThat(response.viewUrl()).isEqualTo("/api/files/44/view?variant=thumb");
        verify(fileViewTicketStore, never()).save(org.mockito.ArgumentMatchers.anyString(), eq(44L), org.mockito.ArgumentMatchers.any());
    }

    // ticket 검증 시 파일 식별자가 다르면 조회를 허용하면 안 된다.
    @Test
    void validate_throws_when_ticket_file_id_does_not_match() {
        // given: 다른 파일 식별자가 저장된 ticket이 있다.
        FileRepository fileRepository = mock(FileRepository.class);
        FileAccessDecisionService accessDecisionService = mock(FileAccessDecisionService.class);
        FileViewTicketStore fileViewTicketStore = mock(FileViewTicketStore.class);
        TicketIdGenerator ticketIdGenerator = mock(TicketIdGenerator.class);
        ObjectStorageProperties properties = new ObjectStorageProperties();
        FileViewTicketService service = new FileViewTicketService(
            fileRepository,
            accessDecisionService,
            fileViewTicketStore,
            properties,
            ticketIdGenerator
        );
        when(fileViewTicketStore.find("valid-ticket")).thenReturn(Optional.of(
            new FileViewTicketStore.FileViewTicketState(99L, Duration.ofSeconds(120L))
        ));

        // when & then: 다른 파일의 ticket이면 404 예외가 발생한다.
        assertThatThrownBy(() -> service.validate(31L, "valid-ticket"))
            .isInstanceOf(ApiException.class)
            .extracting(ex -> ((ApiException) ex).getErrorCode())
            .isEqualTo(ErrorCode.FILE_NOT_FOUND);
    }

    // 보호 파일 ticket 검증은 남은 TTL을 반환해야 한다.
    @Test
    void validate_returns_remaining_ttl_for_matching_ticket() {
        // given: 같은 파일에 연결된 유효한 ticket이 있다.
        FileRepository fileRepository = mock(FileRepository.class);
        FileAccessDecisionService accessDecisionService = mock(FileAccessDecisionService.class);
        FileViewTicketStore fileViewTicketStore = mock(FileViewTicketStore.class);
        TicketIdGenerator ticketIdGenerator = mock(TicketIdGenerator.class);
        ObjectStorageProperties properties = new ObjectStorageProperties();
        FileViewTicketService service = new FileViewTicketService(
            fileRepository,
            accessDecisionService,
            fileViewTicketStore,
            properties,
            ticketIdGenerator
        );
        when(fileViewTicketStore.find("valid-ticket")).thenReturn(Optional.of(
            new FileViewTicketStore.FileViewTicketState(31L, Duration.ofSeconds(87L))
        ));

        // when: ticket을 검증하면
        Duration remainingTtl = service.validate(31L, "valid-ticket");

        // then: 남은 TTL을 그대로 반환한다.
        assertThat(remainingTtl).isEqualTo(Duration.ofSeconds(87L));
    }

    // 배치 발급은 항목별 성공/실패를 반환하고 동일 fileId+variant는 ticket을 한 번만 저장해야 한다.
    @Test
    void issueBatch_returns_partial_results_and_dedupes_ticket_save() {
        FileRepository fileRepository = mock(FileRepository.class);
        FileAccessDecisionService accessDecisionService = mock(FileAccessDecisionService.class);
        FileViewTicketStore fileViewTicketStore = mock(FileViewTicketStore.class);
        TicketIdGenerator ticketIdGenerator = mock(TicketIdGenerator.class);
        ObjectStorageProperties properties = new ObjectStorageProperties();
        properties.setProtectedViewExpireSeconds(120L);
        FileViewTicketService service = new FileViewTicketService(
            fileRepository,
            accessDecisionService,
            fileViewTicketStore,
            properties,
            ticketIdGenerator
        );

        FileEntity protectedFile = createFileEntity(31L, FileClassCode.ARTICLE_CONTENT_IMAGE);
        FileEntity publicFile = createFileEntity(44L, FileClassCode.BOARD_IMAGE);

        when(fileRepository.findByIdAndDeletedAtIsNull(31L)).thenReturn(Optional.of(protectedFile));
        when(fileRepository.findByIdAndDeletedAtIsNull(44L)).thenReturn(Optional.of(publicFile));
        when(fileRepository.findByIdAndDeletedAtIsNull(99L)).thenReturn(Optional.empty());
        when(accessDecisionService.decide(protectedFile)).thenReturn(FileAccessDecision.protectedAccess());
        when(accessDecisionService.decide(publicFile)).thenReturn(FileAccessDecision.publicAccess());
        when(ticketIdGenerator.generate("fv_")).thenReturn("fv_batch_ticket");

        FileViewTicketBatchResponse response = service.issueBatch(List.of(
            new FileViewTicketBatchItemRequest(31L, "medium"),
            new FileViewTicketBatchItemRequest(31L, "medium"),
            new FileViewTicketBatchItemRequest(44L, "thumb"),
            new FileViewTicketBatchItemRequest(99L, null)
        ));

        assertThat(response.items()).hasSize(4);

        FileViewTicketBatchItemResponse firstProtected = response.items().get(0);
        assertThat(firstProtected.success()).isTrue();
        assertThat(firstProtected.protectedFile()).isTrue();
        assertThat(firstProtected.viewUrl()).contains("ticket=fv_batch_ticket");

        FileViewTicketBatchItemResponse duplicateProtected = response.items().get(1);
        assertThat(duplicateProtected.success()).isTrue();
        assertThat(duplicateProtected.viewUrl()).isEqualTo(firstProtected.viewUrl());

        FileViewTicketBatchItemResponse publicItem = response.items().get(2);
        assertThat(publicItem.success()).isTrue();
        assertThat(publicItem.protectedFile()).isFalse();
        assertThat(publicItem.viewUrl()).isEqualTo("/api/files/44/view?variant=thumb");

        FileViewTicketBatchItemResponse missing = response.items().get(3);
        assertThat(missing.success()).isFalse();
        assertThat(missing.errorCode()).isEqualTo(ErrorCode.FILE_NOT_FOUND.getCode());

        verify(fileViewTicketStore, times(1)).save(
            eq("fv_batch_ticket"),
            eq(31L),
            eq(Duration.ofSeconds(120L))
        );
    }

    @Test
    void issueBatch_throws_when_exceeding_configured_max_items() {
        FileRepository fileRepository = mock(FileRepository.class);
        FileAccessDecisionService accessDecisionService = mock(FileAccessDecisionService.class);
        FileViewTicketStore fileViewTicketStore = mock(FileViewTicketStore.class);
        TicketIdGenerator ticketIdGenerator = mock(TicketIdGenerator.class);
        ObjectStorageProperties properties = new ObjectStorageProperties();
        properties.setViewTicketBatchMaxItems(2);
        FileViewTicketService service = new FileViewTicketService(
            fileRepository,
            accessDecisionService,
            fileViewTicketStore,
            properties,
            ticketIdGenerator
        );

        assertThatThrownBy(() -> service.issueBatch(List.of(
            new FileViewTicketBatchItemRequest(1L, null),
            new FileViewTicketBatchItemRequest(2L, null),
            new FileViewTicketBatchItemRequest(3L, null)
        )))
            .isInstanceOf(ApiException.class)
            .extracting(ex -> ((ApiException) ex).getErrorCode())
            .isEqualTo(ErrorCode.COMMON_BAD_REQUEST);
    }

    private FileEntity createFileEntity(Long id, String fileClassCode) {
        FileClassEntity fileClassEntity = FileClassEntity.builder()
            .code(fileClassCode)
            .name("테스트 파일")
            .description("테스트")
            .mediaKind(MediaKind.IMAGE)
            .build();

        FileEntity fileEntity = FileEntity.builder()
            .fileClass(fileClassEntity)
            .fileName("file.png")
            .storageKey("uploads/test/1/file.png")
            .fileSize(1024L)
            .mimeType("image/png")
            .metadataPreserved(false)
            .build();
        ReflectionTestUtils.setField(fileEntity, "id", id);
        return fileEntity;
    }
}
