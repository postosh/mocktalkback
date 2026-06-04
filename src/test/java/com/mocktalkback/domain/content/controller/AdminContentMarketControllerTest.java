package com.mocktalkback.domain.content.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import com.mocktalkback.support.MocktalkWebMvcTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.mocktalkback.domain.content.dto.AdminMarketImportFailureResponse;
import com.mocktalkback.domain.content.dto.AdminMarketImportResponse;
import com.mocktalkback.domain.content.dto.AdminMarketRefreshItemResponse;
import com.mocktalkback.domain.content.dto.AdminMarketRefreshResponse;
import com.mocktalkback.domain.content.service.AdminContentMarketService;
import com.mocktalkback.domain.content.type.MarketInstrumentCode;

@MocktalkWebMvcTest(controllers = AdminContentMarketController.class)
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = {
    "server.port=0",
    "management.server.port=0",
    "management.server.address=127.0.0.1"
})
class AdminContentMarketControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminContentMarketService adminContentMarketService;

    // 수동 최신화 API는 실행 결과 집계를 반환해야 한다.
    @Test
    void refreshNow_returns_result_summary() throws Exception {
        // Given: 최신화 응답이 준비되어 있다.
        AdminMarketRefreshResponse response = new AdminMarketRefreshResponse(
            Instant.parse("2026-03-15T04:00:00Z"),
            5,
            3,
            1,
            1,
            List.of(new AdminMarketRefreshItemResponse(MarketInstrumentCode.USD_KRW, Instant.parse("2026-03-15T03:05:00Z"), "CREATED"))
        );
        when(adminContentMarketService.refreshNow()).thenReturn(response);

        // When: 최신화 API를 호출하면
        ResultActions result = mockMvc.perform(post("/api/admin/contents/market/refresh"));

        // Then: 집계 결과가 반환되어야 한다.
        result.andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.totalCount").value(5))
            .andExpect(jsonPath("$.data.items[0].instrumentCode").value("USD_KRW"));
    }

    // 파일 임포트 API는 선택 종목과 처리 결과를 반환해야 한다.
    @Test
    void importSnapshots_returns_import_summary() throws Exception {
        // Given: 통합 임포트 결과가 준비되어 있다.
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "market.csv",
            "text/csv",
            "instrument_code,observed_at,price_value\nUSD_KRW,2026-03-15,1450.12".getBytes()
        );
        AdminMarketImportResponse response = new AdminMarketImportResponse(
            Instant.parse("2026-03-15T04:10:00Z"),
            "market.csv",
            MarketInstrumentCode.USD_KRW,
            false,
            1,
            1,
            0,
            0,
            0,
            List.of()
        );
        when(adminContentMarketService.importSnapshots(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(MarketInstrumentCode.USD_KRW)))
            .thenReturn(response);

        // When: 선택 종목과 함께 파일 임포트 API를 호출하면
        ResultActions result = mockMvc.perform(multipart("/api/admin/contents/market/import")
            .file(file)
            .param("instrument", "USD_KRW"));

        // Then: 처리 결과 요약이 반환되어야 한다.
        result.andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.selectedInstrument").value("USD_KRW"))
            .andExpect(jsonPath("$.data.createdCount").value(1));
    }

    // 파일 임포트 API는 실패 row가 있으면 실패 목록을 함께 반환해야 한다.
    @Test
    void importSnapshots_returns_failure_rows() throws Exception {
        // Given: 실패 row가 포함된 결과가 준비되어 있다.
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "market.csv",
            "text/csv",
            "instrument_code,observed_at,price_value\nUSD_KRW,invalid,1450.12".getBytes()
        );
        AdminMarketImportResponse response = new AdminMarketImportResponse(
            Instant.parse("2026-03-15T04:10:00Z"),
            "market.csv",
            null,
            true,
            1,
            0,
            0,
            0,
            1,
            List.of(new AdminMarketImportFailureResponse(2, "observed_at 형식이 올바르지 않습니다: invalid"))
        );
        when(adminContentMarketService.importSnapshots(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.isNull()))
            .thenReturn(response);

        // When: 통합 파일 임포트 API를 호출하면
        ResultActions result = mockMvc.perform(multipart("/api/admin/contents/market/import").file(file));

        // Then: 실패 목록이 반환되어야 한다.
        result.andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.failedCount").value(1))
            .andExpect(jsonPath("$.data.failures[0].rowNumber").value(2));
    }
}
