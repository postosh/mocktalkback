package com.mocktalkback.domain.moderation.controller;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import com.mocktalkback.domain.moderation.dto.BoardAdminArticleItemResponse;
import com.mocktalkback.domain.moderation.dto.BoardAdminCommentItemResponse;
import com.mocktalkback.domain.moderation.dto.BoardAdminNoticeUpdateRequest;
import com.mocktalkback.domain.moderation.service.BoardContentAdminService;
import com.mocktalkback.global.common.dto.PageResponse;

@MocktalkWebMvcTest(controllers = BoardContentAdminController.class)
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = {
    "SERVER_PORT=0"
})
class BoardContentAdminControllerTest {

    private static final Instant FIXED_TIME = Instant.parse("2024-01-01T00:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper objectMapper;

    @MockitoBean
    private BoardContentAdminService boardContentAdminService;

    // 게시판 게시글 목록 API는 페이지 응답을 반환해야 한다.
    @Test
    void getArticles_returns_page() throws Exception {
        // Given: 게시글 목록 응답
        BoardAdminArticleItemResponse item = new BoardAdminArticleItemResponse(
            10L,
            3L,
            "홍길동",
            "공지",
            true,
            false,
            FIXED_TIME,
            null
        );
        PageResponse<BoardAdminArticleItemResponse> response = new PageResponse<>(
            List.of(item),
            0,
            10,
            1,
            1,
            false,
            false
        );
        when(boardContentAdminService.findArticles(eq(2L), eq(true), eq(false), eq(3L), eq(0), eq(10)))
            .thenReturn(response);

        // When: 게시글 목록 API 호출
        ResultActions result = mockMvc.perform(get("/api/boards/2/admin/contents/articles")
            .param("reported", "true")
            .param("notice", "false")
            .param("authorId", "3")
            .param("page", "0")
            .param("size", "10"));

        // Then: 응답 데이터 확인
        result.andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.items[0].title").value("공지"));
    }

    // 게시글 공지 설정 API는 응답을 반환해야 한다.
    @Test
    void updateNotice_returns_item() throws Exception {
        // Given: 공지 변경 요청/응답
        BoardAdminNoticeUpdateRequest request = new BoardAdminNoticeUpdateRequest(true);
        BoardAdminArticleItemResponse response = new BoardAdminArticleItemResponse(
            10L,
            3L,
            "홍길동",
            "공지",
            true,
            false,
            FIXED_TIME,
            null
        );
        when(boardContentAdminService.updateNotice(eq(2L), eq(10L), eq(true))).thenReturn(response);

        // When: 공지 변경 API 호출
        ResultActions result = mockMvc.perform(put("/api/boards/2/admin/contents/articles/10/notice")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)));

        // Then: 응답 데이터 확인
        result.andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.notice").value(true));
    }

    // 게시글 삭제 API는 성공 응답을 반환해야 한다.
    @Test
    void deleteArticle_returns_ok() throws Exception {
        // Given: 삭제 처리
        doNothing().when(boardContentAdminService).deleteArticle(2L, 10L);

        // When: 게시글 삭제 API 호출
        ResultActions result = mockMvc.perform(delete("/api/boards/2/admin/contents/articles/10"));

        // Then: 응답 데이터 확인
        result.andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data").value(nullValue()));
    }

    // 게시판 댓글 목록 API는 페이지 응답을 반환해야 한다.
    @Test
    void getComments_returns_page() throws Exception {
        // Given: 댓글 목록 응답
        BoardAdminCommentItemResponse item = new BoardAdminCommentItemResponse(
            22L,
            10L,
            "공지",
            3L,
            "홍길동",
            "댓글 내용",
            0,
            false,
            FIXED_TIME,
            null
        );
        PageResponse<BoardAdminCommentItemResponse> response = new PageResponse<>(
            List.of(item),
            0,
            10,
            1,
            1,
            false,
            false
        );
        when(boardContentAdminService.findComments(eq(2L), eq(true), eq(3L), eq(0), eq(10)))
            .thenReturn(response);

        // When: 댓글 목록 API 호출
        ResultActions result = mockMvc.perform(get("/api/boards/2/admin/contents/comments")
            .param("reported", "true")
            .param("authorId", "3")
            .param("page", "0")
            .param("size", "10"));

        // Then: 응답 데이터 확인
        result.andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.items[0].articleTitle").value("공지"));
    }

    // 댓글 삭제 API는 성공 응답을 반환해야 한다.
    @Test
    void deleteComment_returns_ok() throws Exception {
        // Given: 삭제 처리
        doNothing().when(boardContentAdminService).deleteComment(2L, 22L);

        // When: 댓글 삭제 API 호출
        ResultActions result = mockMvc.perform(delete("/api/boards/2/admin/contents/comments/22"));

        // Then: 응답 데이터 확인
        result.andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data").value(nullValue()));
    }
}
