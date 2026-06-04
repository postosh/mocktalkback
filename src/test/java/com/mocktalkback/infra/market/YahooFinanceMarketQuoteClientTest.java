package com.mocktalkback.infra.market;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import tools.jackson.databind.json.JsonMapper;
import com.mocktalkback.domain.content.config.ContentMarketProperties;
import com.mocktalkback.domain.content.type.MarketInstrumentCode;

class YahooFinanceMarketQuoteClientTest {

    // 야후 파이낸스 응답 파서는 가격과 기준 시각을 읽어야 한다.
    @Test
    void parseQuote_reads_price_and_timestamp() throws Exception {
        // Given: 야후 파이낸스 차트 응답 예시가 있다.
        ContentMarketProperties properties = new ContentMarketProperties();
        YahooFinanceMarketQuoteClient client = new YahooFinanceMarketQuoteClient(RestClient.builder(), new JsonMapper(), properties);
        String responseBody = """
            {
              "chart": {
                "result": [
                  {
                    "meta": {
                      "regularMarketPrice": 1450.12,
                      "regularMarketTime": 1773543900
                    },
                    "timestamp": [1773543900],
                    "indicators": {
                      "quote": [
                        {
                          "close": [1450.12]
                        }
                      ]
                    }
                  }
                ],
                "error": null
              }
            }
            """;

        // When: 응답을 파싱하면
        MarketQuote quote = client.parseQuote(MarketInstrumentCode.USD_KRW, responseBody);

        // Then: 가격과 시각이 추출되어야 한다.
        assertThat(quote.instrumentCode()).isEqualTo(MarketInstrumentCode.USD_KRW);
        assertThat(quote.priceValue()).isEqualByComparingTo("1450.12");
        assertThat(quote.observedAt().getEpochSecond()).isEqualTo(1773543900L);
    }
}