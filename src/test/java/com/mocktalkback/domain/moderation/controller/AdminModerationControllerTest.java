package com.mocktalkback.domain.moderation.controller;

import static org.hamcrest.Matchers.nullValue;
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
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import tools.jackson.databind.json.JsonMapper;
import com.mocktalkback.domain.moderation.dto.AdminAuditLogResponse;
import com.mocktalkback.domain.moderation.dto.ReportDetailResponse;
import com.mocktalkback.domain.moderation.dto.ReportListItemResponse;
import com.mocktalkback.domain.moderation.dto.ReportProcessRequest;
import com.mocktalkback.domain.moderation.dto.SanctionCreateRequest;
import com.mocktalkback.domain.moderation.dto.SanctionResponse;
import com.mocktalkback.domain.moderation.dto.SanctionRevokeRequest;
import com.mocktalkback.domain.moderation.service.ModerationService;
import com.mocktalkback.domain.moderation.type.AdminActionType;
import com.mocktalkback.domain.moderation.type.AdminTargetType;
import com.mocktalkback.domain.moderation.type.ReportReasonCode;
import com.mocktalkback.domain.moderation.type.ReportStatus;
import com.mocktalkback.domain.moderation.type.ReportTargetType;
import com.mocktalkback.domain.moderation.type.SanctionScopeType;
import com.mocktalkback.domain.moderation.type.SanctionType;
import com.mocktalkback.global.common.dto.PageResponse;

@MocktalkWebMvcTest(controllers = AdminModerationController.class)
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = {
    "SERVER_PORT=0"
})
@WithMockUser(roles = "ADMIN")
class AdminModerationControllerTest {

    private static final Instant FIXED_TIME = Instant.parse("2024-01-01T00:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper objectMapper;

    @MockitoBean
    private ModerationService moderationService;

    // 사이트 관리자 신고 목록 API는 페이지 응답을 반환해야 한다.
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
        when(moderationService.getAdminReports(eq(ReportStatus.PENDING), eq(0), eq(10))).thenReturn(response);

        // When: 신고 목록 API 호출
        ResultActions result = mockMvc.perform(get("/api/admin/reports")
            .param("status", "PENDING")
            .param("page", "0")
            .param("size", "10"));

        // Then: 응답 데이터 확인
        result.andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.items[0].reasonCode").value("SPAM"));
    }

    // 사이트 관리자 신고 상세 API는 상세 응답을 반환해야 한다.
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
        when(moderationService.getAdminReport(1L)).thenReturn(response);

        // When: 신고 상세 API 호출
        ResultActions result = mockMvc.perform(get("/api/admin/reports/1"));

        // Then: 응답 데이터 확인
        result.andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.targetType").value("COMMENT"));
    }

    // 사이트 관리자 신고 처리 API는 처리 결과를 반환해야 한다.
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
        when(moderationService.processAdminReport(eq(1L), any(ReportProcessRequest.class), any(), any()))
            .thenReturn(response);

        // When: 신고 처리 API 호출
        ResultActions result = mockMvc.perform(put("/api/admin/reports/1")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)));

        // Then: 응답 데이터 확인
        result.andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.status").value("RESOLVED"));
    }

    // 사이트 관리자 제재 목록 API는 페이지 응답을 반환해야 한다.
    @Test
    void getSanctions_returns_page() throws Exception {
        // Given: 제재 목록 응답
        SanctionResponse item = new SanctionResponse(
            10L,
            7L,
            SanctionScopeType.GLOBAL,
            null,
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
        when(moderationService.getAdminSanctions(eq(SanctionScopeType.GLOBAL), eq(null), eq(0), eq(10))).thenReturn(response);

        // When: 제재 목록 API 호출
        ResultActions result = mockMvc.perform(get("/api/admin/sanctions")
            .param("scopeType", "GLOBAL")
            .param("page", "0")
            .param("size", "10"));

        // Then: 응답 데이터 확인
        result.andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.items[0].sanctionType").value("SUSPEND"));
    }

    // 사이트 관리자 제재 등록 API는 응답을 반환해야 한다.
    @Test
    void createSanction_returns_detail() throws Exception {
        // Given: 제재 등록 요청/응답
        SanctionCreateRequest request = new SanctionCreateRequest(
            7L,
            SanctionScopeType.GLOBAL,
            null,
            SanctionType.BAN,
            "사유",
            FIXED_TIME,
            FIXED_TIME,
            null
        );
        SanctionResponse response = new SanctionResponse(
            11L,
            7L,
            SanctionScopeType.GLOBAL,
            null,
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
        when(moderationService.createAdminSanction(any(SanctionCreateRequest.class), any(), any()))
            .thenReturn(response);

        // When: 제재 등록 API 호출
        ResultActions result = mockMvc.perform(post("/api/admin/sanctions")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)));

        // Then: 응답 데이터 확인
        result.andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.sanctionType").value("BAN"));
    }

    // 사이트 관리자 제재 해제 API는 응답을 반환해야 한다.
    @Test
    void revokeSanction_returns_detail() throws Exception {
        // Given: 제재 해제 요청/응답
        SanctionRevokeRequest request = new SanctionRevokeRequest("해제 사유");
        SanctionResponse response = new SanctionResponse(
            11L,
            7L,
            SanctionScopeType.GLOBAL,
            null,
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
        when(moderationService.revokeAdminSanction(eq(11L), any(SanctionRevokeRequest.class), any(), any()))
            .thenReturn(response);

        // When: 제재 해제 API 호출
        ResultActions result = mockMvc.perform(post("/api/admin/sanctions/11/revoke")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)));

        // Then: 응답 데이터 확인
        result.andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.revokedReason").value("해제 사유"));
    }

    // 사이트 관리자 운영 로그 조회 API는 페이지 응답을 반환해야 한다.
    @Test
    void getAuditLogs_returns_page() throws Exception {
        // Given: 운영 로그 응답
        AdminAuditLogResponse item = new AdminAuditLogResponse(
            5L,
            1L,
            AdminActionType.SANCTION_CREATE,
            AdminTargetType.SANCTION,
            11L,
            null,
            "요약",
            "{}",
            "127.0.0.1",
            "agent",
            FIXED_TIME
        );
        PageResponse<AdminAuditLogResponse> response = new PageResponse<>(
            List.of(item),
            0,
            10,
            1,
            1,
            false,
            false
        );
        when(moderationService.getAdminAuditLogs(
            eq(AdminActionType.SANCTION_CREATE),
            eq(1L),
            eq(AdminTargetType.SANCTION),
            eq(11L),
            eq(FIXED_TIME),
            eq(FIXED_TIME),
            eq(0),
            eq(10)
        )).thenReturn(response);

        // When: 운영 로그 조회 API 호출
        ResultActions result = mockMvc.perform(get("/api/admin/audit-logs")
            .param("actionType", "SANCTION_CREATE")
            .param("actorUserId", "1")
            .param("targetType", "SANCTION")
            .param("targetId", "11")
            .param("fromAt", FIXED_TIME.toString())
            .param("toAt", FIXED_TIME.toString())
            .param("page", "0")
            .param("size", "10"));

        // Then: 응답 데이터 확인
        result.andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.items[0].actionType").value("SANCTION_CREATE"))
            .andExpect(jsonPath("$.error").value(nullValue()));
    }
}
