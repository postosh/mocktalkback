package com.mocktalkback.domain.article.service;

import com.mocktalkback.global.common.dto.ErrorCode;
import com.mocktalkback.global.i18n.ApiException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ArticleHitService {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    @Transactional
    public long increaseAndGet(Long articleId) {
        String sql = """
            update tb_articles
            set hit = hit + 1
            where article_id = :articleId
              and deleted_at is null
            returning hit
            """;

        MapSqlParameterSource params = new MapSqlParameterSource("articleId", articleId);
        Long nextHit = jdbcTemplate.query(sql, params, rs -> {
            if (!rs.next()) {
                return null;
            }
            return rs.getLong("hit");
        });

        if (nextHit == null) {
            throw new ApiException(ErrorCode.ARTICLE_NOT_FOUND);
        }
        return nextHit;
    }
}
