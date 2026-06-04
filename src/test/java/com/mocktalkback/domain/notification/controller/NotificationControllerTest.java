package com.mocktalkback.domain.notification.controller;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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

import com.mocktalkback.domain.notification.dto.NotificationResponse;
import com.mocktalkback.domain.notification.service.NotificationService;
import com.mocktalkback.domain.notification.type.NotificationType;
import com.mocktalkback.domain.notification.type.ReferenceType;
import com.mocktalkback.global.common.dto.PageResponse;

@MocktalkWebMvcTest(controllers = NotificationController.class)
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = {
    "SERVER_PORT=0"
})
class NotificationControllerTest {

    private static final Instant FIXED_TIME = Instant.parse("2024-01-01T00:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NotificationService notificationService;

    // 알림 단건 조회 API는 응답 데이터를 반환해야 한다.
    @Test
    void findById_returns_notification() throws Exception {
        // Given: 알림 응답
        NotificationResponse response = new NotificationResponse(
            100L,
            1L,
            2L,
            "MockTalker",
            "mocktalker",
            NotificationType.ARTICLE_COMMENT,
            "/boards/1/articles/1",
            ReferenceType.ARTICLE,
            10L,
            "Hello world",
            false,
            FIXED_TIME,
            FIXED_TIME
        );
        when(notificationService.findById(100L)).thenReturn(response);

        // When: 알림 조회 API 호출
        ResultActions result = mockMvc.perform(get("/api/notifications/100"));

        // Then: 응답 데이터 확인
        result.andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.notiType").value("ARTICLE_COMMENT"));
    }

    // 알림 목록 조회 API는 페이지 응답을 반환해야 한다.
    @Test
    void findAll_returns_page() throws Exception {
        // Given: 알림 목록 응답
        List<NotificationResponse> responses = List.of(
            new NotificationResponse(
                100L,
                1L,
                2L,
                "MockTalker",
                "mocktalker",
                NotificationType.ARTICLE_COMMENT,
                "/boards/1/articles/1",
                ReferenceType.ARTICLE,
                10L,
                "Hello world",
                false,
                FIXED_TIME,
                FIXED_TIME
            ),
            new NotificationResponse(
                101L,
                1L,
                null,
                null,
                null,
                NotificationType.SYSTEM,
                "/",
                ReferenceType.USER,
                1L,
                null,
                false,
                FIXED_TIME,
                FIXED_TIME
            )
        );
        PageResponse<NotificationResponse> pageResponse = new PageResponse<>(
            responses,
            0,
            10,
            2,
            1,
            false,
            false
        );
        when(notificationService.findAll(0, 10, null)).thenReturn(pageResponse);

        // When: 알림 목록 API 호출
        ResultActions result = mockMvc.perform(get("/api/notifications"));

        // Then: 응답 리스트 확인
        result.andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.items[0].id").value(100L))
            .andExpect(jsonPath("$.data.items[1].id").value(101L));
    }

    // 알림 읽음 필터가 적용된 목록 API는 페이지 응답을 반환해야 한다.
    @Test
    void findAll_with_read_filter_returns_page() throws Exception {
        // Given: 읽음 알림 목록
        List<NotificationResponse> responses = List.of(
            new NotificationResponse(
                102L,
                1L,
                2L,
                "MockTalker",
                "mocktalker",
                NotificationType.COMMENT_REPLY,
                "/boards/1/articles/1",
                ReferenceType.COMMENT,
                21L,
                "Hello world",
                true,
                FIXED_TIME,
                FIXED_TIME
            )
        );
        PageResponse<NotificationResponse> pageResponse = new PageResponse<>(
            responses,
            0,
            10,
            1,
            1,
            false,
            false
        );
        when(notificationService.findAll(0, 10, true)).thenReturn(pageResponse);

        // When: 알림 목록 API 호출
        ResultActions result = mockMvc.perform(get("/api/notifications?read=true"));

        // Then: 응답 리스트 확인
        result.andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.items[0].read").value(true));
    }

    // 알림 읽음 처리 API는 변경된 응답을 반환해야 한다.
    @Test
    void markRead_returns_updated_notification() throws Exception {
        // Given: 알림 읽음 처리 응답
        NotificationResponse response = new NotificationResponse(
            100L,
            1L,
            2L,
            "MockTalker",
            "mocktalker",
            NotificationType.ARTICLE_COMMENT,
            "/boards/1/articles/1",
            ReferenceType.ARTICLE,
            10L,
            "Hello world",
            true,
            FIXED_TIME,
            FIXED_TIME
        );
        when(notificationService.markRead(100L)).thenReturn(response);

        // When: 알림 읽음 처리 API 호출
        ResultActions result = mockMvc.perform(patch("/api/notifications/100/read"));

        // Then: 응답 데이터 확인
        result.andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.read").value(true));
    }

    // 알림 전체 읽음 처리 API는 성공 응답을 반환해야 한다.
    @Test
    void markAllRead_returns_ok() throws Exception {
        // Given: 알림 전체 읽음 처리 준비
        doNothing().when(notificationService).markAllRead();

        // When: 알림 전체 읽음 처리 API 호출
        ResultActions result = mockMvc.perform(patch("/api/notifications/read-all"));

        // Then: 성공 응답 확인
        result.andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
    }

    // 동일 redirectUrl 알림 읽음 처리 API는 성공 응답을 반환해야 한다.
    @Test
    void markReadByRedirectUrl_returns_ok() throws Exception {
        // Given: redirectUrl 기준 읽음 처리 준비
        String redirectUrl = "/b/general/articles/100";
        doNothing().when(notificationService).markReadByRedirectUrl(redirectUrl);

        // When: redirectUrl 기준 읽음 처리 API 호출
        ResultActions result = mockMvc.perform(patch("/api/notifications/read-by-redirect-url")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "redirectUrl": "/b/general/articles/100"
                }
                """));

        // Then: 성공 응답 확인
        result.andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data").value(nullValue()));
    }

    // 알림 삭제 API는 성공 응답을 반환해야 한다.
    @Test
    void delete_returns_ok() throws Exception {
        // Given: 알림 삭제 준비
        doNothing().when(notificationService).delete(100L);

        // When: 알림 삭제 API 호출
        ResultActions result = mockMvc.perform(delete("/api/notifications/100"));

        // Then: 성공 응답 확인
        result.andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data").value(nullValue()));
    }
}
