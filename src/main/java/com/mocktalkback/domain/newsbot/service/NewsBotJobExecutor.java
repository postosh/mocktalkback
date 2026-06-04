package com.mocktalkback.domain.newsbot.service;

import java.time.Clock;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.mocktalkback.domain.newsbot.dto.AdminNewsBotJobRunResponse;
import com.mocktalkback.domain.newsbot.entity.NewsCollectionJobEntity;
import com.mocktalkback.domain.newsbot.repository.NewsCollectionJobRepository;
import com.mocktalkback.global.common.dto.ErrorCode;
import com.mocktalkback.global.i18n.ApiException;

@Service
public class NewsBotJobExecutor {

    private final NewsCollectionJobRepository newsCollectionJobRepository;
    private final NewsBotSourceFetchService newsBotSourceFetchService;
    private final NewsBotJobExecutionClaimService newsBotJobExecutionClaimService;
    private final NewsBotJobExecutionPersistenceService newsBotJobExecutionPersistenceService;
    private final Clock clock;

    @Autowired
    public NewsBotJobExecutor(
        NewsCollectionJobRepository newsCollectionJobRepository,
        NewsBotSourceFetchService newsBotSourceFetchService,
        NewsBotJobExecutionClaimService newsBotJobExecutionClaimService,
        NewsBotJobExecutionPersistenceService newsBotJobExecutionPersistenceService
    ) {
        this(
            newsCollectionJobRepository,
            newsBotSourceFetchService,
            newsBotJobExecutionClaimService,
            newsBotJobExecutionPersistenceService,
            Clock.systemUTC()
        );
    }

    NewsBotJobExecutor(
        NewsCollectionJobRepository newsCollectionJobRepository,
        NewsBotSourceFetchService newsBotSourceFetchService,
        NewsBotJobExecutionClaimService newsBotJobExecutionClaimService,
        NewsBotJobExecutionPersistenceService newsBotJobExecutionPersistenceService,
        Clock clock
    ) {
        this.newsCollectionJobRepository = newsCollectionJobRepository;
        this.newsBotSourceFetchService = newsBotSourceFetchService;
        this.newsBotJobExecutionClaimService = newsBotJobExecutionClaimService;
        this.newsBotJobExecutionPersistenceService = newsBotJobExecutionPersistenceService;
        this.clock = clock;
    }

    public AdminNewsBotJobRunResponse runNow(Long jobId) {
        Instant startedAt = clock.instant();
        newsBotJobExecutionClaimService.claimManualRun(jobId, startedAt);
        return execute(jobId, startedAt);
    }

    public AdminNewsBotJobRunResponse runScheduled(Long jobId) {
        Instant startedAt = clock.instant();
        if (!newsBotJobExecutionClaimService.claimScheduledRun(jobId, startedAt)) {
            return null;
        }
        return execute(jobId, startedAt);
    }

    private AdminNewsBotJobRunResponse execute(Long jobId, Instant startedAt) {
        NewsCollectionJobEntity job = newsCollectionJobRepository.findById(jobId)
            .orElseThrow(() -> new ApiException(ErrorCode.NEWSBOT_JOB_NOT_FOUND));

        java.util.List<NewsBotSourceItem> items;
        try {
            items = newsBotSourceFetchService.fetchItems(job);
        } catch (Exception exception) {
            return newsBotJobExecutionPersistenceService.markFetchFailure(jobId, startedAt, extractMessage(exception));
        }

        return newsBotJobExecutionPersistenceService.processFetchedItems(jobId, startedAt, items);
    }

    private String extractMessage(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return exception.getClass().getSimpleName();
        }
        return message;
    }
}
