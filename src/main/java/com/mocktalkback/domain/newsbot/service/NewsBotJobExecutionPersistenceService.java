package com.mocktalkback.domain.newsbot.service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mocktalkback.domain.article.entity.ArticleCategoryEntity;
import com.mocktalkback.domain.article.entity.ArticleEntity;
import com.mocktalkback.domain.board.entity.BoardEntity;
import com.mocktalkback.domain.newsbot.dto.AdminNewsBotJobRunResponse;
import com.mocktalkback.domain.newsbot.entity.NewsCollectedItemEntity;
import com.mocktalkback.domain.newsbot.entity.NewsCollectionJobEntity;
import com.mocktalkback.domain.newsbot.repository.NewsCollectedItemRepository;
import com.mocktalkback.domain.newsbot.repository.NewsCollectionJobRepository;
import com.mocktalkback.domain.newsbot.type.NewsJobExecutionStatus;
import com.mocktalkback.global.common.dto.ErrorCode;
import com.mocktalkback.global.i18n.ApiException;

@Service
public class NewsBotJobExecutionPersistenceService {

    private final NewsCollectionJobRepository newsCollectionJobRepository;
    private final NewsCollectedItemRepository newsCollectedItemRepository;
    private final NewsBotBoardProvisionService newsBotBoardProvisionService;
    private final NewsBotArticlePublishService newsBotArticlePublishService;
    private final NewsBotPayloadHasher newsBotPayloadHasher;
    private final Clock clock;

    @Autowired
    public NewsBotJobExecutionPersistenceService(
        NewsCollectionJobRepository newsCollectionJobRepository,
        NewsCollectedItemRepository newsCollectedItemRepository,
        NewsBotBoardProvisionService newsBotBoardProvisionService,
        NewsBotArticlePublishService newsBotArticlePublishService,
        NewsBotPayloadHasher newsBotPayloadHasher
    ) {
        this(
            newsCollectionJobRepository,
            newsCollectedItemRepository,
            newsBotBoardProvisionService,
            newsBotArticlePublishService,
            newsBotPayloadHasher,
            Clock.systemUTC()
        );
    }

    NewsBotJobExecutionPersistenceService(
        NewsCollectionJobRepository newsCollectionJobRepository,
        NewsCollectedItemRepository newsCollectedItemRepository,
        NewsBotBoardProvisionService newsBotBoardProvisionService,
        NewsBotArticlePublishService newsBotArticlePublishService,
        NewsBotPayloadHasher newsBotPayloadHasher,
        Clock clock
    ) {
        this.newsCollectionJobRepository = newsCollectionJobRepository;
        this.newsCollectedItemRepository = newsCollectedItemRepository;
        this.newsBotBoardProvisionService = newsBotBoardProvisionService;
        this.newsBotArticlePublishService = newsBotArticlePublishService;
        this.newsBotPayloadHasher = newsBotPayloadHasher;
        this.clock = clock;
    }

    @Transactional
    public AdminNewsBotJobRunResponse markFetchFailure(Long jobId, Instant startedAt, String errorMessage) {
        NewsCollectionJobEntity job = getJob(jobId);
        return failJob(job, startedAt, 0, 0, 0, 0, errorMessage);
    }

    @Transactional
    public AdminNewsBotJobRunResponse processFetchedItems(Long jobId, Instant startedAt, List<NewsBotSourceItem> items) {
        NewsCollectionJobEntity job = getJob(jobId);

        BoardEntity board;
        ArticleCategoryEntity category;
        try {
            board = newsBotBoardProvisionService.ensureBoard(job);
            category = newsBotBoardProvisionService.ensureCategory(board, job);
        } catch (Exception exception) {
            return failJob(job, startedAt, items.size(), 0, 0, 0, extractMessage(exception));
        }

        int createdCount = 0;
        int updatedCount = 0;
        int skippedCount = 0;
        int failedCount = 0;
        String firstErrorMessage = null;

        for (NewsBotSourceItem item : items) {
            Instant collectedAt = clock.instant();
            String payloadHash = newsBotPayloadHasher.hash(item);
            NewsCollectedItemEntity collectedItem = newsCollectedItemRepository
                .findByNewsJob_IdAndExternalItemKey(job.getId(), item.externalItemKey())
                .orElse(null);

            if (collectedItem == null) {
                collectedItem = NewsCollectedItemEntity.create(
                    job,
                    item.externalItemKey(),
                    item.externalUrl(),
                    item.title(),
                    payloadHash,
                    item.publishedAt(),
                    item.sourceUpdatedAt(),
                    collectedAt
                );
                newsCollectedItemRepository.save(collectedItem);
            } else {
                collectedItem.refreshSourceSnapshot(
                    item.externalUrl(),
                    item.title(),
                    item.publishedAt(),
                    item.sourceUpdatedAt()
                );
            }

            try {
                if (payloadHash.equals(collectedItem.getPayloadHash()) && collectedItem.getArticle() != null) {
                    collectedItem.markSkipped(payloadHash, collectedAt);
                    skippedCount += 1;
                    continue;
                }

                ArticleEntity article;
                if (collectedItem.getArticle() == null) {
                    article = newsBotArticlePublishService.createArticle(
                        job,
                        board,
                        job.getAuthorUser(),
                        category,
                        item
                    );
                    collectedItem.markCreated(article, payloadHash, collectedAt);
                    createdCount += 1;
                } else {
                    article = newsBotArticlePublishService.updateArticle(job, collectedItem.getArticle(), category, item);
                    collectedItem.markUpdated(article, payloadHash, collectedAt);
                    updatedCount += 1;
                }
            } catch (Exception exception) {
                String errorMessage = extractMessage(exception);
                collectedItem.markFailure(payloadHash, collectedAt, errorMessage);
                failedCount += 1;
                if (firstErrorMessage == null) {
                    firstErrorMessage = errorMessage;
                }
            }
        }

        Instant finishedAt = clock.instant();
        Instant nextRunAt = finishedAt.plus(job.getCollectIntervalMinutes(), ChronoUnit.MINUTES);
        if (firstErrorMessage == null) {
            job.markSuccess(finishedAt, nextRunAt);
            return new AdminNewsBotJobRunResponse(
                job.getId(),
                finishedAt,
                items.size(),
                createdCount,
                updatedCount,
                skippedCount,
                failedCount,
                NewsJobExecutionStatus.SUCCESS,
                null
            );
        }

        job.markFailure(finishedAt, nextRunAt, firstErrorMessage);
        return new AdminNewsBotJobRunResponse(
            job.getId(),
            finishedAt,
            items.size(),
            createdCount,
            updatedCount,
            skippedCount,
            failedCount,
            NewsJobExecutionStatus.FAILED,
            firstErrorMessage
        );
    }

    private NewsCollectionJobEntity getJob(Long jobId) {
        return newsCollectionJobRepository.findById(jobId)
            .orElseThrow(() -> new ApiException(ErrorCode.NEWSBOT_JOB_NOT_FOUND));
    }

    private AdminNewsBotJobRunResponse failJob(
        NewsCollectionJobEntity job,
        Instant startedAt,
        int fetchedCount,
        int createdCount,
        int updatedCount,
        int skippedCount,
        String errorMessage
    ) {
        Instant finishedAt = clock.instant();
        Instant nextRunAt = finishedAt.plus(job.getCollectIntervalMinutes(), ChronoUnit.MINUTES);
        job.markFailure(finishedAt, nextRunAt, errorMessage);
        return new AdminNewsBotJobRunResponse(
            job.getId(),
            startedAt,
            fetchedCount,
            createdCount,
            updatedCount,
            skippedCount,
            fetchedCount == 0 ? 0 : fetchedCount,
            NewsJobExecutionStatus.FAILED,
            errorMessage
        );
    }

    private String extractMessage(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return exception.getClass().getSimpleName();
        }
        return message;
    }
}
