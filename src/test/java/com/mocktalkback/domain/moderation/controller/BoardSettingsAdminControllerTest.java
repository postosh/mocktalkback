package com.mocktalkback.domain.moderation.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import com.mocktalkback.domain.board.dto.BoardResponse;
import com.mocktalkback.domain.board.type.BoardArticleWritePolicy;
import com.mocktalkback.domain.board.type.BoardVisibility;
import com.mocktalkback.domain.moderation.dto.BoardAdminSettingsUpdateRequest;
import com.mocktalkback.domain.moderation.service.BoardSettingsAdminService;

@MocktalkWebMvcTest(controllers = BoardSettingsAdminController.class)
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = {
    "SERVER_PORT=0"
})
class BoardSettingsAdminControllerTest {

    private static final Instant FIXED_TIME = Instant.parse("2024-01-01T00:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper objectMapper;

    @MockitoBean
    private BoardSettingsAdminService boardSettingsAdminService;

    // 게시판 설정 조회 API는 응답을 반환해야 한다.
    @Test
    void getSettings_returns_board() throws Exception {
        // Given: 게시판 설정 응답
        BoardResponse response = new BoardResponse(
            2L,
            "공지사항",
            "notice",
            "notice board",
            BoardVisibility.PUBLIC,
            BoardArticleWritePolicy.ALL_AUTHENTICATED,
            FIXED_TIME,
            FIXED_TIME,
            null,
            null
        );
        when(boardSettingsAdminService.getSettings(2L)).thenReturn(response);

        // When: 게시판 설정 조회 API 호출
        ResultActions result = mockMvc.perform(get("/api/boards/2/admin/settings"));

        // Then: 응답 데이터 확인
        result.andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.slug").value("notice"));
    }

    // 게시판 설정 수정 API는 응답을 반환해야 한다.
    @Test
    void updateSettings_returns_board() throws Exception {
        // Given: 게시판 설정 수정 요청/응답
        BoardAdminSettingsUpdateRequest request = new BoardAdminSettingsUpdateRequest(
            "공지사항",
            "notice board",
            BoardVisibility.PUBLIC,
            BoardArticleWritePolicy.MODERATOR
        );
        BoardResponse response = new BoardResponse(
            2L,
            "공지사항",
            "notice",
            "notice board",
            BoardVisibility.PUBLIC,
            BoardArticleWritePolicy.MODERATOR,
            FIXED_TIME,
            FIXED_TIME,
            null,
            null
        );
        when(boardSettingsAdminService.updateSettings(eq(2L), any(BoardAdminSettingsUpdateRequest.class))).thenReturn(response);

        // When: 게시판 설정 수정 API 호출
        ResultActions result = mockMvc.perform(put("/api/boards/2/admin/settings")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)));

        // Then: 응답 데이터 확인
        result.andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.boardName").value("공지사항"));
    }

    // 게시판 대표 이미지 삭제 API는 응답을 반환해야 한다.
    @Test
    void deleteImage_returns_board() throws Exception {
        // Given: 이미지 삭제 응답
        BoardResponse response = new BoardResponse(
            2L,
            "공지사항",
            "notice",
            "notice board",
            BoardVisibility.PUBLIC,
            BoardArticleWritePolicy.ALL_AUTHENTICATED,
            FIXED_TIME,
            FIXED_TIME,
            null,
            null
        );
        when(boardSettingsAdminService.deleteBoardImage(2L)).thenReturn(response);

        // When: 게시판 대표 이미지 삭제 API 호출
        ResultActions result = mockMvc.perform(delete("/api/boards/2/admin/settings/image"));

        // Then: 응답 데이터 확인
        result.andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.id").value(2L));
    }
}
