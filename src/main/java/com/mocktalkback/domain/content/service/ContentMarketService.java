package com.mocktalkback.domain.content.service;

import com.mocktalkback.global.common.dto.ErrorCode;
import com.mocktalkback.global.i18n.ApiException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.math.BigDecimal;
import java.math.RoundingMode;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mocktalkback.domain.content.dto.MarketOverviewItemResponse;
import com.mocktalkback.domain.content.dto.MarketOverviewResponse;
import com.mocktalkback.domain.content.dto.MarketSeriesPointResponse;
import com.mocktalkback.domain.content.dto.MarketSeriesResponse;
import com.mocktalkback.domain.content.entity.MarketSnapshotEntity;
import com.mocktalkback.domain.content.repository.MarketSnapshotRepository;
import com.mocktalkback.domain.content.type.MarketInstrumentCode;
import com.mocktalkback.domain.content.type.MarketSeriesPeriod;

@Service
public class ContentMarketService {

    private final MarketSnapshotRepository marketSnapshotRepository;
    private final ContentMarketSeriesCacheStore contentMarketSeriesCacheStore;
    private final Clock clock;

    @Autowired
    public ContentMarketService(
        MarketSnapshotRepository marketSnapshotRepository,
        ContentMarketSeriesCacheStore contentMarketSeriesCacheStore
    ) {
        this(marketSnapshotRepository, contentMarketSeriesCacheStore, Clock.systemUTC());
    }

    ContentMarketService(
        MarketSnapshotRepository marketSnapshotRepository,
        ContentMarketSeriesCacheStore contentMarketSeriesCacheStore,
        Clock clock
    ) {
        this.marketSnapshotRepository = marketSnapshotRepository;
        this.contentMarketSeriesCacheStore = contentMarketSeriesCacheStore;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public MarketOverviewResponse findOverview() {
        List<MarketOverviewItemResponse> items = new ArrayList<>();
        Instant lastObservedAt = null;

        for (MarketInstrumentCode instrumentCode : MarketInstrumentCode.displayTargets()) {
            Optional<MarketSnapshotEntity> latestSnapshot = marketSnapshotRepository.findFirstByInstrumentCodeOrderByObservedAtDesc(instrumentCode);
            if (latestSnapshot.isEmpty()) {
                continue;
            }

            MarketSnapshotEntity snapshot = latestSnapshot.get();
            Optional<MarketSnapshotEntity> previousSnapshot = marketSnapshotRepository
                .findFirstByInstrumentCodeAndObservedAtLessThanOrderByObservedAtDesc(
                    snapshot.getInstrumentCode(),
                    snapshot.getObservedAt()
                );
            items.add(new MarketOverviewItemResponse(
                snapshot.getInstrumentCode(),
                snapshot.getInstrumentCode().getDisplayName(),
                snapshot.getMarketGroup(),
                snapshot.getBaseCurrency(),
                snapshot.getQuoteCurrency(),
                snapshot.getInstrumentCode().getUnitLabel(),
                snapshot.getPriceValue(),
                resolveChangeValue(snapshot.getPriceValue(), previousSnapshot),
                resolveChangeRate(snapshot.getPriceValue(), previousSnapshot),
                snapshot.getObservedAt()
            ));
            if (lastObservedAt == null || snapshot.getObservedAt().isAfter(lastObservedAt)) {
                lastObservedAt = snapshot.getObservedAt();
            }
        }

        items.sort(Comparator.comparingInt(item -> MarketInstrumentCode.displayTargets().indexOf(item.instrumentCode())));
        return new MarketOverviewResponse(lastObservedAt, items);
    }

    @Transactional(readOnly = true)
    public MarketSeriesResponse findSeries(
        MarketInstrumentCode instrumentCode,
        MarketSeriesPeriod period,
        LocalDate startDate,
        LocalDate endDate
    ) {
        MarketSeriesRange range = resolveRange(period, startDate, endDate);
        Optional<MarketSeriesResponse> cachedResponse = contentMarketSeriesCacheStore.find(instrumentCode, range.period());
        if (cachedResponse.isPresent()) {
            return cachedResponse.get();
        }

        List<MarketSnapshotEntity> snapshots = marketSnapshotRepository
            .findByInstrumentCodeAndObservedAtGreaterThanEqualAndObservedAtLessThanOrderByObservedAtAsc(
                instrumentCode,
                range.startObservedAt(),
                range.endObservedAtExclusive()
            );
        List<MarketSeriesPointResponse> points = snapshots.stream()
            .map(snapshot -> new MarketSeriesPointResponse(snapshot.getObservedAt(), snapshot.getPriceValue()))
            .toList();
        Instant lastObservedAt = snapshots.isEmpty() ? null : snapshots.get(snapshots.size() - 1).getObservedAt();

        MarketSeriesResponse response = new MarketSeriesResponse(
            instrumentCode,
            instrumentCode.getDisplayName(),
            instrumentCode.getMarketGroup(),
            instrumentCode.getUnitLabel(),
            range.period(),
            lastObservedAt,
            points
        );
        contentMarketSeriesCacheStore.put(response);
        return response;
    }

    private MarketSeriesRange resolveRange(
        MarketSeriesPeriod period,
        LocalDate startDate,
        LocalDate endDate
    ) {
        if (period == MarketSeriesPeriod.CUSTOM) {
            if (startDate == null || endDate == null) {
                throw new ApiException(ErrorCode.MARKET_PERIOD_RANGE_INVALID);
            }
            if (startDate.isAfter(endDate)) {
                throw new ApiException(ErrorCode.MARKET_PERIOD_START_AFTER_END);
            }
            return new MarketSeriesRange(
                MarketSeriesPeriod.CUSTOM,
                startDate.atStartOfDay(ZoneOffset.UTC).toInstant(),
                endDate.plusDays(1L).atStartOfDay(ZoneOffset.UTC).toInstant()
            );
        }

        if (startDate != null || endDate != null) {
            throw new ApiException(ErrorCode.MARKET_PERIOD_CUSTOM_INVALID);
        }

        LocalDate today = LocalDate.now(clock);
        LocalDate resolvedStartDate = today.minusDays(period.getDays() - 1L);

        return new MarketSeriesRange(
            period,
            resolvedStartDate.atStartOfDay(ZoneOffset.UTC).toInstant(),
            today.plusDays(1L).atStartOfDay(ZoneOffset.UTC).toInstant()
        );
    }

    private record MarketSeriesRange(
        MarketSeriesPeriod period,
        Instant startObservedAt,
        Instant endObservedAtExclusive
    ) {
    }

    private BigDecimal resolveChangeValue(BigDecimal currentPriceValue, Optional<MarketSnapshotEntity> previousSnapshot) {
        return previousSnapshot
            .map(snapshot -> currentPriceValue.subtract(snapshot.getPriceValue()).setScale(8, RoundingMode.HALF_UP))
            .orElse(null);
    }

    private BigDecimal resolveChangeRate(BigDecimal currentPriceValue, Optional<MarketSnapshotEntity> previousSnapshot) {
        return previousSnapshot
            .filter(snapshot -> snapshot.getPriceValue().compareTo(BigDecimal.ZERO) > 0)
            .map(snapshot -> currentPriceValue
                .subtract(snapshot.getPriceValue())
                .divide(snapshot.getPriceValue(), 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100L))
                .setScale(6, RoundingMode.HALF_UP))
            .orElse(null);
    }
}
