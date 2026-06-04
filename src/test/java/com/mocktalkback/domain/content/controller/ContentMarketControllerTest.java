package com.mocktalkback.domain.content.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import com.mocktalkback.support.MocktalkWebMvcTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.mocktalkback.domain.content.dto.MarketOverviewItemResponse;
import com.mocktalkback.domain.content.dto.MarketOverviewResponse;
import com.mocktalkback.domain.content.dto.MarketSeriesPointResponse;
import com.mocktalkback.domain.content.dto.MarketSeriesResponse;
import com.mocktalkback.domain.content.service.ContentMarketService;
import com.mocktalkback.domain.content.type.MarketGroup;
import com.mocktalkback.domain.content.type.MarketInstrumentCode;
import com.mocktalkback.domain.content.type.MarketSeriesPeriod;

@MocktalkWebMvcTest(controllers = ContentMarketController.class)
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = {
    "server.port=0",
    "management.server.port=0",
    "management.server.address=127.0.0.1"
})
class ContentMarketControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ContentMarketService contentMarketService;

    // 환율/금 시세 요약 API는 종목 목록을 반환해야 한다.
    @Test
    void findOverview_returns_items() throws Exception {
        // Given: 시세 요약 응답이 준비되어 있다.
        MarketOverviewResponse response = new MarketOverviewResponse(
            Instant.parse("2026-03-15T03:05:00Z"),
            List.of(
                new MarketOverviewItemResponse(
                    MarketInstrumentCode.USD_KRW,
                    "USD/KRW",
                    MarketGroup.FX,
                    "USD",
                    "KRW",
                    "원",
                    new BigDecimal("1450.12000000"),
                    new BigDecimal("10.50000000"),
                    new BigDecimal("0.729000"),
                    Instant.parse("2026-03-15T03:05:00Z")
                )
            )
        );
        when(contentMarketService.findOverview()).thenReturn(response);

        // When: 요약 API를 호출하면
        ResultActions result = mockMvc.perform(get("/api/contents/market/overview"));

        // Then: 종목 목록이 반환되어야 한다.
        result.andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.items[0].instrumentCode").value("USD_KRW"))
            .andExpect(jsonPath("$.data.items[0].priceValue").value(1450.12));
    }

    // 환율/금 시계열 API는 선택한 기간의 포인트 목록을 반환해야 한다.
    @Test
    void findSeries_returns_points() throws Exception {
        // Given: 7일 시계열 응답이 준비되어 있다.
        MarketSeriesResponse response = new MarketSeriesResponse(
            MarketInstrumentCode.XAU_USD,
            "금 시세 (USD)",
            MarketGroup.METAL,
            "달러",
            MarketSeriesPeriod.WEEK,
            Instant.parse("2026-03-15T03:05:00Z"),
            List.of(
                new MarketSeriesPointResponse(Instant.parse("2026-03-10T03:05:00Z"), new BigDecimal("2988.50000000")),
                new MarketSeriesPointResponse(Instant.parse("2026-03-15T03:05:00Z"), new BigDecimal("3012.12000000"))
            )
        );
        when(contentMarketService.findSeries(MarketInstrumentCode.XAU_USD, MarketSeriesPeriod.WEEK, null, null)).thenReturn(response);

        // When: 시계열 API를 호출하면
        ResultActions result = mockMvc.perform(get("/api/contents/market/series")
            .param("instrument", "XAU_USD")
            .param("period", "WEEK"));

        // Then: 기간에 맞는 포인트가 반환되어야 한다.
        result.andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.instrumentCode").value("XAU_USD"))
            .andExpect(jsonPath("$.data.points[1].value").value(3012.12));
    }

    // 환율/금 시계열 API는 직접 선택 기간 파라미터도 처리해야 한다.
    @Test
    void findSeries_returns_points_for_custom_range() throws Exception {
        // Given: 직접 선택 기간 시계열 응답이 준비되어 있다.
        MarketSeriesResponse response = new MarketSeriesResponse(
            MarketInstrumentCode.USD_KRW,
            "USD/KRW",
            MarketGroup.FX,
            "원",
            MarketSeriesPeriod.CUSTOM,
            Instant.parse("2026-03-15T03:05:00Z"),
            List.of(
                new MarketSeriesPointResponse(Instant.parse("2026-03-01T03:05:00Z"), new BigDecimal("1448.10000000")),
                new MarketSeriesPointResponse(Instant.parse("2026-03-15T03:05:00Z"), new BigDecimal("1450.12000000"))
            )
        );
        when(contentMarketService.findSeries(
            MarketInstrumentCode.USD_KRW,
            MarketSeriesPeriod.CUSTOM,
            LocalDate.parse("2026-03-01"),
            LocalDate.parse("2026-03-15")
        )).thenReturn(response);

        // When: 직접 선택 기간으로 시계열 API를 호출하면
        ResultActions result = mockMvc.perform(get("/api/contents/market/series")
            .param("instrument", "USD_KRW")
            .param("period", "CUSTOM")
            .param("startDate", "2026-03-01")
            .param("endDate", "2026-03-15"));

        // Then: 직접 선택 기간 포인트가 반환되어야 한다.
        result.andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.period").value("CUSTOM"))
            .andExpect(jsonPath("$.data.points[0].value").value(1448.1));
    }
}
