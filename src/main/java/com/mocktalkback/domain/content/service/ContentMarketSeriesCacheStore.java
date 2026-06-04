package com.mocktalkback.domain.content.service;

import com.mocktalkback.global.common.dto.ErrorCode;
import com.mocktalkback.global.i18n.ApiException;
import java.time.Duration;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import com.mocktalkback.domain.content.config.ContentMarketProperties;
import com.mocktalkback.domain.content.dto.MarketSeriesResponse;
import com.mocktalkback.domain.content.type.MarketInstrumentCode;
import com.mocktalkback.domain.content.type.MarketSeriesPeriod;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContentMarketSeriesCacheStore {

    private static final String KEY_PREFIX = "content:market:series:v1:";
    private static final Set<MarketSeriesPeriod> CACHEABLE_PERIODS = EnumSet.of(
        MarketSeriesPeriod.YEAR,
        MarketSeriesPeriod.THREE_YEAR,
        MarketSeriesPeriod.FIVE_YEAR,
        MarketSeriesPeriod.TEN_YEAR
    );

    private final StringRedisTemplate stringRedisTemplate;
    private final JsonMapper objectMapper;
    private final ContentMarketProperties contentMarketProperties;

    public Optional<MarketSeriesResponse> find(MarketInstrumentCode instrumentCode, MarketSeriesPeriod period) {
        if (!isCacheEnabled() || !isCacheable(period)) {
            return Optional.empty();
        }

        String raw = stringRedisTemplate.opsForValue().get(key(instrumentCode, period));
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }

        try {
            return Optional.of(objectMapper.readValue(raw, MarketSeriesResponse.class));
        } catch (JacksonException ex) {
            log.warn("시세 시계열 캐시 역직렬화에 실패해 캐시를 삭제합니다. instrument={}, period={}", instrumentCode, period, ex);
            stringRedisTemplate.delete(key(instrumentCode, period));
            return Optional.empty();
        }
    }

    public void put(MarketSeriesResponse response) {
        if (!isCacheEnabled() || !isCacheable(response.period())) {
            return;
        }

        try {
            String raw = objectMapper.writeValueAsString(response);
            stringRedisTemplate.opsForValue().set(
                key(response.instrumentCode(), response.period()),
                raw,
                Duration.ofSeconds(contentMarketProperties.getSeriesCacheTtlSeconds())
            );
        } catch (JacksonException ex) {
            log.warn("시세 시계열 캐시 직렬화에 실패해 저장을 건너뜁니다. instrument={}, period={}", response.instrumentCode(), response.period(), ex);
        }
    }

    public void evictPresetSeries(Collection<MarketInstrumentCode> instrumentCodes) {
        if (!isCacheEnabled() || instrumentCodes == null || instrumentCodes.isEmpty()) {
            return;
        }

        Set<String> keys = instrumentCodes.stream()
            .flatMap(instrumentCode -> CACHEABLE_PERIODS.stream().map(period -> key(instrumentCode, period)))
            .collect(Collectors.toSet());

        if (!keys.isEmpty()) {
            stringRedisTemplate.delete(keys);
        }
    }

    boolean isCacheable(MarketSeriesPeriod period) {
        return period != null && CACHEABLE_PERIODS.contains(period);
    }

    private boolean isCacheEnabled() {
        return contentMarketProperties.isSeriesCacheEnabled()
            && contentMarketProperties.getSeriesCacheTtlSeconds() > 0L;
    }

    private String key(MarketInstrumentCode instrumentCode, MarketSeriesPeriod period) {
        if (instrumentCode == null) {
            throw new ApiException(ErrorCode.MARKET_SYMBOL_EMPTY);
        }
        if (period == null) {
            throw new ApiException(ErrorCode.MARKET_PERIOD_CODE_EMPTY);
        }
        return KEY_PREFIX + instrumentCode.name() + ":" + period.name();
    }
}
