package com.mocktalkback.infra.market;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import com.mocktalkback.domain.content.config.ContentMarketProperties;
import com.mocktalkback.domain.content.type.MarketInstrumentCode;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class YahooFinanceMarketQuoteClient implements ExternalMarketQuoteClient {

    private static final String PROVIDER_NAME = "YAHOO_FINANCE";

    private final RestClient restClient;
    private final JsonMapper objectMapper;
    private final ContentMarketProperties properties;

    public YahooFinanceMarketQuoteClient(
        RestClient.Builder restClientBuilder,
        JsonMapper objectMapper,
        ContentMarketProperties properties
    ) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.getConnectTimeoutMs());
        requestFactory.setReadTimeout(properties.getReadTimeoutMs());
        this.restClient = restClientBuilder
            .baseUrl(properties.getBaseUrl())
            .requestFactory(requestFactory)
            .defaultHeader("User-Agent", properties.getUserAgent())
            .build();
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public Optional<MarketQuote> fetchQuote(MarketInstrumentCode instrumentCode) {
        if (instrumentCode.isDerived() || instrumentCode.getYahooSymbol() == null) {
            return Optional.empty();
        }

        try {
            String responseBody = restClient.get()
                .uri(uriBuilder -> uriBuilder
                    .path(properties.getChartPath())
                    .queryParam("interval", properties.getInterval())
                    .queryParam("range", properties.getRange())
                    .build(instrumentCode.getYahooSymbol()))
                .retrieve()
                .body(String.class);
            return Optional.of(parseQuote(instrumentCode, responseBody));
        } catch (Exception exception) {
            log.warn("야후 파이낸스 시세 조회에 실패했습니다. instrument={}", instrumentCode, exception);
            return Optional.empty();
        }
    }

    MarketQuote parseQuote(MarketInstrumentCode instrumentCode, String responseBody) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode chartNode = root.path("chart");
        JsonNode errorNode = chartNode.path("error");
        if (!errorNode.isMissingNode() && !errorNode.isNull()) {
            throw new IllegalStateException("공급자 응답에 오류가 포함되어 있습니다.");
        }

        JsonNode resultNode = chartNode.path("result");
        if (!resultNode.isArray() || resultNode.isEmpty()) {
            throw new IllegalStateException("공급자 응답에 시세 결과가 없습니다.");
        }

        JsonNode firstResult = resultNode.get(0);
        BigDecimal priceValue = extractPriceValue(firstResult);
        Instant observedAt = extractObservedAt(firstResult);
        return new MarketQuote(instrumentCode, priceValue, observedAt, PROVIDER_NAME);
    }

    private BigDecimal extractPriceValue(JsonNode resultNode) {
        JsonNode metaNode = resultNode.path("meta");
        JsonNode regularMarketPriceNode = metaNode.path("regularMarketPrice");
        if (regularMarketPriceNode.isNumber()) {
            return regularMarketPriceNode.decimalValue();
        }

        JsonNode closeNode = resultNode.path("indicators").path("quote");
        if (closeNode.isArray() && !closeNode.isEmpty()) {
            JsonNode closeValues = closeNode.get(0).path("close");
            if (closeValues.isArray()) {
                for (int index = closeValues.size() - 1; index >= 0; index--) {
                    JsonNode closeValue = closeValues.get(index);
                    if (closeValue != null && closeValue.isNumber()) {
                        return closeValue.decimalValue();
                    }
                }
            }
        }

        throw new IllegalStateException("공급자 응답에 가격 값이 없습니다.");
    }

    private Instant extractObservedAt(JsonNode resultNode) {
        JsonNode metaNode = resultNode.path("meta");
        JsonNode regularMarketTimeNode = metaNode.path("regularMarketTime");
        if (regularMarketTimeNode.isNumber() && regularMarketTimeNode.asLong() > 0L) {
            return Instant.ofEpochSecond(regularMarketTimeNode.asLong());
        }

        JsonNode timestampNode = resultNode.path("timestamp");
        if (timestampNode.isArray()) {
            for (int index = timestampNode.size() - 1; index >= 0; index--) {
                JsonNode timestampValue = timestampNode.get(index);
                if (timestampValue != null && timestampValue.isNumber() && timestampValue.asLong() > 0L) {
                    return Instant.ofEpochSecond(timestampValue.asLong());
                }
            }
        }

        throw new IllegalStateException("공급자 응답에 기준 시각이 없습니다.");
    }
}
