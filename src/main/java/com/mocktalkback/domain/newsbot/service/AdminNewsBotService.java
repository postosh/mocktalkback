package com.mocktalkback.domain.newsbot.service;

import com.mocktalkback.global.common.dto.ErrorCode;
import com.mocktalkback.global.i18n.ApiException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.mocktalkback.domain.newsbot.config.NewsBotProperties;
import com.mocktalkback.domain.newsbot.dto.AdminNewsBotJobResponse;
import com.mocktalkback.domain.newsbot.dto.AdminNewsBotJobRunResponse;
import com.mocktalkback.domain.newsbot.dto.AdminNewsBotJobToggleRequest;
import com.mocktalkback.domain.newsbot.dto.AdminNewsBotJobUpsertRequest;
import com.mocktalkback.domain.newsbot.entity.NewsCollectionJobEntity;
import com.mocktalkback.domain.newsbot.repository.NewsCollectionJobRepository;
import com.mocktalkback.domain.user.entity.UserEntity;
import com.mocktalkback.domain.user.repository.UserRepository;
import com.mocktalkback.global.auth.CurrentUserService;

@Service
public class AdminNewsBotService {

    private static final String SYSTEM_AUTHOR_LOGIN_ID = "news_bot";

    private final NewsCollectionJobRepository newsCollectionJobRepository;
    private final UserRepository userRepository;
    private final CurrentUserService currentUserService;
    private final NewsBotSourceFetchService newsBotSourceFetchService;
    private final NewsBotJobExecutor newsBotJobExecutor;
    private final NewsBotProperties newsBotProperties;
    private final Clock clock;

    @Autowired
    public AdminNewsBotService(
        NewsCollectionJobRepository newsCollectionJobRepository,
        UserRepository userRepository,
        CurrentUserService currentUserService,
        NewsBotSourceFetchService newsBotSourceFetchService,
        NewsBotJobExecutor newsBotJobExecutor,
        NewsBotProperties newsBotProperties
    ) {
        this(
            newsCollectionJobRepository,
            userRepository,
            currentUserService,
            newsBotSourceFetchService,
            newsBotJobExecutor,
            newsBotProperties,
            Clock.systemUTC()
        );
    }

    AdminNewsBotService(
        NewsCollectionJobRepository newsCollectionJobRepository,
        UserRepository userRepository,
        CurrentUserService currentUserService,
        NewsBotSourceFetchService newsBotSourceFetchService,
        NewsBotJobExecutor newsBotJobExecutor,
        NewsBotProperties newsBotProperties,
        Clock clock
    ) {
        this.newsCollectionJobRepository = newsCollectionJobRepository;
        this.userRepository = userRepository;
        this.currentUserService = currentUserService;
        this.newsBotSourceFetchService = newsBotSourceFetchService;
        this.newsBotJobExecutor = newsBotJobExecutor;
        this.newsBotProperties = newsBotProperties;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<AdminNewsBotJobResponse> getJobs() {
        return newsCollectionJobRepository.findAllByOrderByCreatedAtDesc().stream()
            .map(this::toResponse)
            .toList();
    }

    @Transactional
    public AdminNewsBotJobResponse createJob(AdminNewsBotJobUpsertRequest request) {
        UserEntity actor = getActor();
        UserEntity author = getSystemAuthor();
        validateRequest(request);
        if (newsCollectionJobRepository.existsByJobName(request.jobName().trim())) {
            throw new ApiException(ErrorCode.NEWSBOT_JOB_NAME_DUPLICATE);
        }

        Instant now = clock.instant();
        NewsCollectionJobEntity job = NewsCollectionJobEntity.create(
            request.jobName().trim(),
            request.sourceType(),
            newsBotSourceFetchService.serialize(request.sourceConfig()),
            normalizeSlug(request.targetBoardSlug()),
            normalizeText(request.targetBoardName()),
            normalizeText(request.targetCategoryName()),
            author,
            actor,
            request.collectIntervalMinutes(),
            request.fetchLimit(),
            request.autoCreateBoard(),
            request.autoCreateCategory(),
            resolveTimezone(request.timezone()),
            now
        );
        NewsCollectionJobEntity saved = newsCollectionJobRepository.save(job);
        return toResponse(saved);
    }

    @Transactional
    public AdminNewsBotJobResponse updateJob(Long jobId, AdminNewsBotJobUpsertRequest request) {
        UserEntity actor = getActor();
        UserEntity author = getSystemAuthor();
        validateRequest(request);

        NewsCollectionJobEntity job = newsCollectionJobRepository.findById(jobId)
            .orElseThrow(() -> new ApiException(ErrorCode.NEWSBOT_JOB_NOT_FOUND));

        String newJobName = request.jobName().trim();
        if (!job.getJobName().equals(newJobName) && newsCollectionJobRepository.existsByJobName(newJobName)) {
            throw new ApiException(ErrorCode.NEWSBOT_JOB_NAME_DUPLICATE);
        }

        job.updateJob(
            newJobName,
            request.sourceType(),
            newsBotSourceFetchService.serialize(request.sourceConfig()),
            normalizeSlug(request.targetBoardSlug()),
            normalizeText(request.targetBoardName()),
            normalizeText(request.targetCategoryName()),
            author,
            actor,
            request.collectIntervalMinutes(),
            request.fetchLimit(),
            request.autoCreateBoard(),
            request.autoCreateCategory(),
            resolveTimezone(request.timezone()),
            clock.instant()
        );
        return toResponse(job);
    }

    @Transactional
    public AdminNewsBotJobResponse changeEnabled(Long jobId, AdminNewsBotJobToggleRequest request) {
        UserEntity actor = getActor();
        NewsCollectionJobEntity job = newsCollectionJobRepository.findById(jobId)
            .orElseThrow(() -> new ApiException(ErrorCode.NEWSBOT_JOB_NOT_FOUND));

        job.changeEnabled(request.enabled(), actor);
        if (request.enabled()) {
            job.scheduleNextRun(clock.instant(), actor);
        }
        return toResponse(job);
    }

    @Transactional
    public AdminNewsBotJobRunResponse runNow(Long jobId) {
        getActor();
        return newsBotJobExecutor.runNow(jobId);
    }

    private void validateRequest(AdminNewsBotJobUpsertRequest request) {
        newsBotSourceFetchService.validateConfig(request.sourceType(), request.sourceConfig());
        String resolvedTimezone = resolveTimezone(request.timezone());
        ZoneId.of(resolvedTimezone);
        if (request.autoCreateBoard() && !StringUtils.hasText(request.targetBoardName())) {
            throw new ApiException(ErrorCode.NEWSBOT_TARGET_BOARD_NAME_REQUIRED);
        }
    }

    private UserEntity getActor() {
        Long userId = currentUserService.getUserId();
        return userRepository.findByIdAndDeletedAtIsNull(userId)
            .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
    }

    private UserEntity getSystemAuthor() {
        return userRepository.findByLoginId(SYSTEM_AUTHOR_LOGIN_ID)
            .orElseThrow(() -> new IllegalStateException("news_bot 시스템 계정을 찾을 수 없습니다."));
    }

    private AdminNewsBotJobResponse toResponse(NewsCollectionJobEntity entity) {
        UserEntity author = entity.getAuthorUser();
        return new AdminNewsBotJobResponse(
            entity.getId(),
            entity.getJobName(),
            entity.getSourceType(),
            newsBotSourceFetchService.deserialize(entity.getSourceConfigJson()),
            entity.getTargetBoardSlug(),
            entity.getTargetBoardName(),
            entity.getTargetCategoryName(),
            author.getId(),
            resolveDisplayName(author),
            entity.isEnabled(),
            entity.getCollectIntervalMinutes(),
            entity.getFetchLimit(),
            entity.isAutoCreateBoard(),
            entity.isAutoCreateCategory(),
            entity.getTimezone(),
            entity.getLastStartedAt(),
            entity.getLastFinishedAt(),
            entity.getLastSuccessAt(),
            entity.getNextRunAt(),
            entity.getLastStatus(),
            entity.getLastErrorMessage(),
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }

    private String resolveDisplayName(UserEntity user) {
        if (StringUtils.hasText(user.getDisplayName())) {
            return user.getDisplayName().trim();
        }
        return user.getUserName();
    }

    private String normalizeSlug(String targetBoardSlug) {
        return targetBoardSlug.trim().toLowerCase();
    }

    private String normalizeText(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private String resolveTimezone(String timezone) {
        if (!StringUtils.hasText(timezone)) {
            return newsBotProperties.getDefaultTimezone();
        }
        return timezone.trim();
    }
}
