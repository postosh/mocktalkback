package com.mocktalkback.domain.article.service;

import com.mocktalkback.global.common.dto.ErrorCode;
import com.mocktalkback.global.i18n.ApiException;
import java.time.Duration;
import java.util.List;
import java.util.Set;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ArticleTrendingStore {

    private final StringRedisTemplate stringRedisTemplate;

    public void incrementScore(String key, Long articleId, double delta, Duration ttl) {
        stringRedisTemplate.opsForZSet().incrementScore(key, member(articleId), delta);
        stringRedisTemplate.expire(key, ttl);
    }

    public List<RankedArticle> findTopArticles(String key, int limit) {
        if (limit <= 0) {
            return List.of();
        }

        Set<TypedTuple<String>> tuples = stringRedisTemplate.opsForZSet()
            .reverseRangeWithScores(key, 0, limit - 1L);

        if (tuples == null || tuples.isEmpty()) {
            return List.of();
        }

        return tuples.stream()
            .filter(tuple -> tuple.getValue() != null && tuple.getScore() != null)
            .map(tuple -> new RankedArticle(Long.valueOf(tuple.getValue()), tuple.getScore()))
            .toList();
    }

    private String member(Long articleId) {
        if (articleId == null) {
            throw new ApiException(ErrorCode.ARTICLE_ID_EMPTY);
        }
        return String.valueOf(articleId);
    }

    public record RankedArticle(Long articleId, double score) {
    }
}
