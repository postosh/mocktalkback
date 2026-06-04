package com.mocktalkback.domain.article.service;

import com.mocktalkback.global.common.dto.ErrorCode;
import com.mocktalkback.global.i18n.ApiException;
import java.time.Duration;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ArticleViewDedupeStore {

    private static final String KEY_PREFIX = "article:view:dedupe:v1:";

    private final StringRedisTemplate stringRedisTemplate;

    public boolean markViewed(Long articleId, String viewerKey, Duration ttl) {
        if (articleId == null) {
            throw new ApiException(ErrorCode.ARTICLE_ID_EMPTY);
        }
        if (!StringUtils.hasText(viewerKey)) {
            throw new ApiException(ErrorCode.ARTICLE_ID_EMPTY);
        }
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new ApiException(ErrorCode.UPLOAD_FILE_SIZE_INVALID);
        }
        Boolean stored = stringRedisTemplate.opsForValue().setIfAbsent(key(articleId, viewerKey), "1", ttl);
        return Boolean.TRUE.equals(stored);
    }

    private String key(Long articleId, String viewerKey) {
        return KEY_PREFIX + articleId + ":" + viewerKey.trim();
    }
}
