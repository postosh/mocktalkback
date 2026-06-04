package com.mocktalkback.domain.newsbot.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.mocktalkback.global.common.dto.ErrorCode;
import com.mocktalkback.global.i18n.ApiException;

import com.mocktalkback.domain.newsbot.config.NewsBotProperties;
import com.mocktalkback.domain.newsbot.repository.NewsCollectionJobRepository;
import com.mocktalkback.domain.newsbot.type.NewsJobExecutionStatus;

@ExtendWith(MockitoExtension.class)
class NewsBotJobExecutionClaimServiceTest {

    @Mock
    private NewsCollectionJobRepository newsCollectionJobRepository;

    // 수동 실행 선점은 이미 RUNNING 인 잡이면 409 충돌을 반환해야 한다.
    @Test
    void claimManualRun_throwsConflictWhenJobIsAlreadyRunning() {
        // Given: 선점 update 가 실패했지만 잡 자체는 존재한다.
        NewsBotProperties properties = new NewsBotProperties();
        properties.setRunLockTimeoutMinutes(30);
        NewsBotJobExecutionClaimService service = new NewsBotJobExecutionClaimService(
            newsCollectionJobRepository,
            properties
        );
        Instant startedAt = Instant.parse("2026-03-15T10:00:00Z");
        Instant staleStartedBefore = startedAt.minusSeconds(30L * 60L);

        when(newsCollectionJobRepository.claimManualRun(
            1L,
            startedAt,
            staleStartedBefore,
            NewsJobExecutionStatus.RUNNING
        )).thenReturn(0);
        when(newsCollectionJobRepository.existsById(1L)).thenReturn(true);

        // When & Then: 이미 실행 중인 잡이면 충돌이어야 한다.
        assertThatThrownBy(() -> service.claimManualRun(1L, startedAt))
            .isInstanceOf(ApiException.class)
            .extracting(ex -> ((ApiException) ex).getErrorCode())
            .isEqualTo(ErrorCode.NEWSBOT_JOB_ALREADY_RUNNING);
        verify(newsCollectionJobRepository).existsById(1L);
    }

    // 스케줄 실행 선점은 stale timeout 을 반영해 원자적으로 update 해야 한다.
    @Test
    void claimScheduledRun_usesConfiguredStaleTimeout() {
        // Given: stale timeout 이 15분으로 설정되어 있다.
        NewsBotProperties properties = new NewsBotProperties();
        properties.setRunLockTimeoutMinutes(15);
        NewsBotJobExecutionClaimService service = new NewsBotJobExecutionClaimService(
            newsCollectionJobRepository,
            properties
        );
        Instant startedAt = Instant.parse("2026-03-15T10:00:00Z");
        Instant staleStartedBefore = startedAt.minusSeconds(15L * 60L);

        when(newsCollectionJobRepository.claimScheduledRun(
            1L,
            startedAt,
            staleStartedBefore,
            NewsJobExecutionStatus.RUNNING
        )).thenReturn(1);

        // When: 스케줄 실행 선점을 시도하면
        service.claimScheduledRun(1L, startedAt);

        // Then: 설정된 stale timeout 기준으로 선점 update 를 시도해야 한다.
        verify(newsCollectionJobRepository).claimScheduledRun(
            1L,
            startedAt,
            staleStartedBefore,
            NewsJobExecutionStatus.RUNNING
        );
    }
}
