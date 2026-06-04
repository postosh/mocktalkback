package com.mocktalkback.domain.file.service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Component
public class StorageDeleteRetryQueueStore {

    private static final String RETRY_ZSET_KEY = "storage:delete:retry:zset";
    private static final String RETRY_JOB_PREFIX = "storage:delete:retry:item:";
    private static final String DLQ_ZSET_KEY = "storage:delete:dlq:zset";
    private static final String DLQ_JOB_PREFIX = "storage:delete:dlq:item:";

    private final StringRedisTemplate stringRedisTemplate;
    private final JsonMapper objectMapper;

    public StorageDeleteRetryQueueStore(
        StringRedisTemplate stringRedisTemplate,
        JsonMapper objectMapper
    ) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
    }

    public Optional<StorageDeleteRetryJob> findRetryJob(String jobId) {
        if (!StringUtils.hasText(jobId)) {
            return Optional.empty();
        }
        String raw = stringRedisTemplate.opsForValue().get(retryJobKey(jobId));
        if (!StringUtils.hasText(raw)) {
            return Optional.empty();
        }
        return Optional.of(deserialize(raw));
    }

    public void upsertRetryJob(StorageDeleteRetryJob job) {
        if (job == null || !StringUtils.hasText(job.jobId())) {
            throw new IllegalArgumentException("삭제 재시도 작업 정보가 비어있습니다.");
        }
        String serialized = serialize(job);
        stringRedisTemplate.opsForValue().set(retryJobKey(job.jobId()), serialized);
        stringRedisTemplate.opsForZSet().add(RETRY_ZSET_KEY, job.jobId(), job.nextRetryAtEpochSeconds());
    }

    public List<StorageDeleteRetryJob> popDueRetryJobs(long nowEpochSeconds, int batchSize) {
        if (batchSize <= 0) {
            return List.of();
        }
        Set<String> jobIds = stringRedisTemplate.opsForZSet()
            .rangeByScore(RETRY_ZSET_KEY, 0, nowEpochSeconds, 0, batchSize);
        if (jobIds == null || jobIds.isEmpty()) {
            return List.of();
        }

        List<StorageDeleteRetryJob> jobs = new ArrayList<>(jobIds.size());
        for (String jobId : jobIds) {
            if (!StringUtils.hasText(jobId)) {
                continue;
            }
            Long removed = stringRedisTemplate.opsForZSet().remove(RETRY_ZSET_KEY, jobId);
            if (removed == null || removed <= 0L) {
                continue;
            }
            String raw = stringRedisTemplate.opsForValue().get(retryJobKey(jobId));
            if (!StringUtils.hasText(raw)) {
                continue;
            }
            jobs.add(deserialize(raw));
        }
        return jobs;
    }

    public void deleteRetryJob(String jobId) {
        if (!StringUtils.hasText(jobId)) {
            return;
        }
        String normalized = jobId.trim();
        stringRedisTemplate.opsForZSet().remove(RETRY_ZSET_KEY, normalized);
        stringRedisTemplate.delete(retryJobKey(normalized));
    }

    public void moveToDlq(StorageDeleteRetryJob job, long movedAtEpochSeconds, long retentionSeconds) {
        if (job == null || !StringUtils.hasText(job.jobId())) {
            return;
        }
        deleteRetryJob(job.jobId());
        String serialized = serialize(job);
        String dlqKey = dlqJobKey(job.jobId());
        if (retentionSeconds > 0L) {
            stringRedisTemplate.opsForValue().set(dlqKey, serialized, Duration.ofSeconds(retentionSeconds));
        } else {
            stringRedisTemplate.opsForValue().set(dlqKey, serialized);
        }
        stringRedisTemplate.opsForZSet().add(DLQ_ZSET_KEY, job.jobId(), movedAtEpochSeconds);
    }

    public void pruneDlq(long beforeEpochSeconds, int batchSize) {
        if (batchSize <= 0) {
            return;
        }
        Set<String> jobIds = stringRedisTemplate.opsForZSet()
            .rangeByScore(DLQ_ZSET_KEY, 0, beforeEpochSeconds, 0, batchSize);
        if (jobIds == null || jobIds.isEmpty()) {
            return;
        }
        for (String jobId : jobIds) {
            if (!StringUtils.hasText(jobId)) {
                continue;
            }
            Long removed = stringRedisTemplate.opsForZSet().remove(DLQ_ZSET_KEY, jobId);
            if (removed == null || removed <= 0L) {
                continue;
            }
            stringRedisTemplate.delete(dlqJobKey(jobId));
        }
    }

    private String retryJobKey(String jobId) {
        return RETRY_JOB_PREFIX + jobId.trim();
    }

    private String dlqJobKey(String jobId) {
        return DLQ_JOB_PREFIX + jobId.trim();
    }

    private String serialize(StorageDeleteRetryJob job) {
        try {
            return objectMapper.writeValueAsString(job);
        } catch (JacksonException ex) {
            throw new IllegalStateException("삭제 재시도 큐 직렬화에 실패했습니다.");
        }
    }

    private StorageDeleteRetryJob deserialize(String raw) {
        try {
            return objectMapper.readValue(raw, StorageDeleteRetryJob.class);
        } catch (JacksonException ex) {
            throw new IllegalStateException("삭제 재시도 큐 역직렬화에 실패했습니다.");
        }
    }
}

