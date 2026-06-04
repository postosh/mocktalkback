package com.mocktalkback.domain.moderation.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

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
import com.mocktalkback.domain.moderation.dto.ReportCreateRequest;
import com.mocktalkback.domain.moderation.dto.ReportDetailResponse;
import com.mocktalkback.domain.moderation.service.ModerationService;
import com.mocktalkback.domain.moderation.type.ReportReasonCode;
import com.mocktalkback.domain.moderation.type.ReportStatus;
import com.mocktalkback.domain.moderation.type.ReportTargetType;

@MocktalkWebMvcTest(controllers = ReportController.class)
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = {
    "SERVER_PORT=0"
})
class ReportControllerTest {

    private static final Instant FIXED_TIME = Instant.parse("2024-01-01T00:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper objectMapper;

    @MockitoBean
    private ModerationService moderationService;

    // 신고 접수 API는 상세 응답을 반환해야 한다.
    @Test
    void createReport_returns_detail() throws Exception {
        // Given: 신고 접수 요청/응답
        ReportCreateRequest request = new ReportCreateRequest(
            ReportTargetType.ARTICLE,
            10L,
            ReportReasonCode.SPAM,
            "상세 사유"
        );
        ReportDetailResponse response = new ReportDetailResponse(
            1L,
            ReportStatus.PENDING,
            ReportTargetType.ARTICLE,
            10L,
            ReportReasonCode.SPAM,
            "상세 사유",
            null,
            3L,
            7L,
            2L,
            null,
            null,
            null,
            FIXED_TIME,
            FIXED_TIME
        );
        when(moderationService.createReport(any(ReportCreateRequest.class))).thenReturn(response);

        // When: 신고 접수 API 호출
        ResultActions result = mockMvc.perform(post("/api/reports")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)));

        // Then: 응답 데이터 확인
        result.andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.targetType").value("ARTICLE"));
    }
}
