package com.mocktalkback.domain.moderation.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import com.mocktalkback.support.MocktalkWebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import tools.jackson.databind.json.JsonMapper;
import com.mocktalkback.domain.moderation.dto.ReportDetailResponse;
import com.mocktalkback.domain.moderation.dto.ReportListItemResponse;
import com.mocktalkback.domain.moderation.dto.ReportProcessRequest;
import com.mocktalkback.domain.moderation.dto.SanctionCreateRequest;
import com.mocktalkback.domain.moderation.dto.SanctionResponse;
import com.mocktalkback.domain.moderation.dto.SanctionRevokeRequest;
import com.mocktalkback.domain.moderation.service.ModerationService;
import com.mocktalkback.domain.moderation.type.ReportReasonCode;
import com.mocktalkback.domain.moderation.type.ReportStatus;
import com.mocktalkback.domain.moderation.type.ReportTargetType;
import com.mocktalkback.domain.moderation.type.SanctionScopeType;
import com.mocktalkback.domain.moderation.type.SanctionType;
import com.mocktalkback.global.common.dto.PageResponse;

@MocktalkWebMvcTest(controllers = BoardModerationController.class)
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = {
    "SERVER_PORT=0"
})
class BoardModerationControllerTest {

    private static final Instant FIXED_TIME = Instant.parse("2024-01-01T00:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper objectMapper;

    @MockitoBean
    private ModerationService moderationService;

    // 게시판 신고 목록 API는 페이지 응답을 반환해야 한다.
    @Test
    void getReports_returns_page() throws Exception {
        // Given: 신고 목록 응답
        ReportListItemResponse item = new ReportListItemResponse(
            1L,
            ReportStatus.PENDING,
            ReportTargetType.ARTICLE,
            10L,
            ReportReasonCode.SPAM,
            3L,
            7L,
            2L,
            null,
            FIXED_TIME
        );
        PageResponse<ReportListItemResponse> response = new PageResponse<>(
            List.of(item),
            0,
            10,
            1,
            1,
            false,
            false
        );
        when(moderationService.getBoardReports(eq(2L), eq(ReportStatus.PENDING), eq(0), eq(10))).thenReturn(response);

        // When: 게시판 신고 목록 API 호출
        ResultActions result = mockMvc.perform(get("/api/boards/2/admin/reports")
            .param("status", "PENDING")
            .param("page", "0")
            .param("size", "10"));

        // Then: 응답 데이터 확인
        result.andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.items[0].targetType").value("ARTICLE"));
    }

    // 게시판 신고 상세 API는 상세 응답을 반환해야 한다.
    @Test
    void getReport_returns_detail() throws Exception {
        // Given: 신고 상세 응답
        ReportDetailResponse response = new ReportDetailResponse(
            1L,
            ReportStatus.IN_REVIEW,
            ReportTargetType.COMMENT,
            22L,
            ReportReasonCode.ABUSE,
            "상세 사유",
            "{}",
            3L,
            7L,
            2L,
            1L,
            "처리 메모",
            FIXED_TIME,
            FIXED_TIME,
            FIXED_TIME
        );
        when(moderationService.getBoardReport(2L, 1L)).thenReturn(response);

        // When: 게시판 신고 상세 API 호출
        ResultActions result = mockMvc.perform(get("/api/boards/2/admin/reports/1"));

        // Then: 응답 데이터 확인
        result.andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.targetId").value(22L));
    }

    // 게시판 신고 처리 API는 처리 결과를 반환해야 한다.
    @Test
    void processReport_returns_detail() throws Exception {
        // Given: 신고 처리 요청/응답
        ReportProcessRequest request = new ReportProcessRequest(ReportStatus.RESOLVED, "처리 메모");
        ReportDetailResponse response = new ReportDetailResponse(
            1L,
            ReportStatus.RESOLVED,
            ReportTargetType.ARTICLE,
            10L,
            ReportReasonCode.SPAM,
            null,
            null,
            3L,
            7L,
            2L,
            1L,
            "처리 메모",
            FIXED_TIME,
            FIXED_TIME,
            FIXED_TIME
        );
        when(moderationService.processBoardReport(eq(2L), eq(1L), any(ReportProcessRequest.class), any(), any()))
            .thenReturn(response);

        // When: 게시판 신고 처리 API 호출
        ResultActions result = mockMvc.perform(put("/api/boards/2/admin/reports/1")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)));

        // Then: 응답 데이터 확인
        result.andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.status").value("RESOLVED"));
    }

    // 게시판 제재 목록 API는 페이지 응답을 반환해야 한다.
    @Test
    void getSanctions_returns_page() throws Exception {
        // Given: 제재 목록 응답
        SanctionResponse item = new SanctionResponse(
            10L,
            7L,
            SanctionScopeType.BOARD,
            2L,
            SanctionType.SUSPEND,
            "사유",
            FIXED_TIME,
            FIXED_TIME,
            1L,
            1L,
            null,
            null,
            null,
            FIXED_TIME,
            FIXED_TIME
        );
        PageResponse<SanctionResponse> response = new PageResponse<>(
            List.of(item),
            0,
            10,
            1,
            1,
            false,
            false
        );
        when(moderationService.getBoardSanctions(eq(2L), eq(0), eq(10))).thenReturn(response);

        // When: 게시판 제재 목록 API 호출
        ResultActions result = mockMvc.perform(get("/api/boards/2/admin/sanctions")
            .param("page", "0")
            .param("size", "10"));

        // Then: 응답 데이터 확인
        result.andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.items[0].scopeType").value("BOARD"));
    }

    // 게시판 제재 등록 API는 응답을 반환해야 한다.
    @Test
    void createSanction_returns_detail() throws Exception {
        // Given: 제재 등록 요청/응답
        SanctionCreateRequest request = new SanctionCreateRequest(
            7L,
            SanctionScopeType.BOARD,
            2L,
            SanctionType.BAN,
            "사유",
            FIXED_TIME,
            FIXED_TIME,
            null
        );
        SanctionResponse response = new SanctionResponse(
            11L,
            7L,
            SanctionScopeType.BOARD,
            2L,
            SanctionType.BAN,
            "사유",
            FIXED_TIME,
            FIXED_TIME,
            null,
            1L,
            null,
            null,
            null,
            FIXED_TIME,
            FIXED_TIME
        );
        when(moderationService.createBoardSanction(eq(2L), any(SanctionCreateRequest.class), any(), any()))
            .thenReturn(response);

        // When: 게시판 제재 등록 API 호출
        ResultActions result = mockMvc.perform(post("/api/boards/2/admin/sanctions")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)));

        // Then: 응답 데이터 확인
        result.andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.scopeType").value("BOARD"));
    }

    // 게시판 제재 해제 API는 응답을 반환해야 한다.
    @Test
    void revokeSanction_returns_detail() throws Exception {
        // Given: 제재 해제 요청/응답
        SanctionRevokeRequest request = new SanctionRevokeRequest("해제 사유");
        SanctionResponse response = new SanctionResponse(
            11L,
            7L,
            SanctionScopeType.BOARD,
            2L,
            SanctionType.BAN,
            "사유",
            FIXED_TIME,
            FIXED_TIME,
            null,
            1L,
            FIXED_TIME,
            1L,
            "해제 사유",
            FIXED_TIME,
            FIXED_TIME
        );
        when(moderationService.revokeBoardSanction(eq(2L), eq(11L), any(SanctionRevokeRequest.class), any(), any()))
            .thenReturn(response);

        // When: 게시판 제재 해제 API 호출
        ResultActions result = mockMvc.perform(post("/api/boards/2/admin/sanctions/11/revoke")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)));

        // Then: 응답 데이터 확인
        result.andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.revokedReason").value("해제 사유"));
    }
}
