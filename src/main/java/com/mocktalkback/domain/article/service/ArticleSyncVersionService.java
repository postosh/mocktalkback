package com.mocktalkback.domain.article.service;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mocktalkback.global.common.dto.ErrorCode;
import com.mocktalkback.global.i18n.ApiException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ArticleSyncVersionService {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    @Transactional
    public long increaseAndGet(Long articleId) {
        String sql = """
            update tb_articles
            set sync_version = sync_version + 1
            where article_id = :articleId
              and deleted_at is null
            returning sync_version
            """;

        MapSqlParameterSource params = new MapSqlParameterSource("articleId", articleId);
        Long nextSyncVersion = jdbcTemplate.query(sql, params, rs -> {
            if (!rs.next()) {
                return null;
            }
            return rs.getLong("sync_version");
        });

        if (nextSyncVersion == null) {
            throw new ApiException(ErrorCode.ARTICLE_NOT_FOUND);
        }
        return nextSyncVersion;
    }
}
