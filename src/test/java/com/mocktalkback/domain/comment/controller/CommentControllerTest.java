package com.mocktalkback.domain.comment.controller;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import com.mocktalkback.domain.comment.dto.CommentCreateRequest;
import com.mocktalkback.domain.comment.dto.CommentPageResponse;
import com.mocktalkback.domain.comment.dto.CommentReactionSummaryResponse;
import com.mocktalkback.domain.comment.dto.CommentReactionToggleRequest;
import com.mocktalkback.domain.comment.dto.CommentSnapshotResponse;
import com.mocktalkback.domain.comment.dto.CommentTreeResponse;
import com.mocktalkback.domain.comment.dto.CommentUpdateRequest;
import com.mocktalkback.domain.comment.service.CommentService;

@MocktalkWebMvcTest(controllers = CommentController.class)
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = {
    "SERVER_PORT=0"
})
class CommentControllerTest {

    private static final Instant FIXED_TIME = Instant.parse("2024-01-01T00:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper objectMapper;

    @MockitoBean
    private CommentService commentService;

    // 댓글 생성 API는 성공 응답을 반환해야 한다.
    @Test
    void create_returns_ok() throws Exception {
        // Given: 댓글 생성 요청
        CommentCreateRequest request = new CommentCreateRequest("comment content");
        CommentTreeResponse response = new CommentTreeResponse(
            100L,
            1L,
            "작성자",
            "comment content",
            0,
            null,
            100L,
            FIXED_TIME,
            FIXED_TIME,
            null,
            0L,
            0L,
            (short) 0,
            List.of()
        );
        when(commentService.createRoot(10L, request)).thenReturn(response);

        // When: 댓글 생성 API 호출
        ResultActions result = mockMvc.perform(post("/api/articles/10/comments")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)));

        // Then: 응답 데이터 확인
        result.andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.id").value(100L));
    }

    // 답글 생성 API는 성공 응답을 반환해야 한다.
    @Test
    void createReply_returns_ok() throws Exception {
        // Given: 답글 생성 요청
        CommentCreateRequest request = new CommentCreateRequest("reply content");
        CommentTreeResponse response = new CommentTreeResponse(
            101L,
            1L,
            "작성자",
            "reply content",
            1,
            100L,
            100L,
            FIXED_TIME,
            FIXED_TIME,
            null,
            0L,
            0L,
            (short) 0,
            List.of()
        );
        when(commentService.createReply(10L, 100L, request)).thenReturn(response);

        // When: 답글 생성 API 호출
        ResultActions result = mockMvc.perform(post("/api/articles/10/comments/100")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)));

        // Then: 응답 데이터 확인
        result.andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.id").value(101L));
    }

    // 댓글 목록 조회 API는 리스트 응답을 반환해야 한다.
    @Test
    void findAll_returns_list() throws Exception {
        // Given: 댓글 목록 응답
        CommentTreeResponse reply = new CommentTreeResponse(
            101L,
            1L,
            "작성자",
            "reply",
            1,
            100L,
            100L,
            FIXED_TIME,
            FIXED_TIME,
            null,
            0L,
            0L,
            (short) 0,
            List.of()
        );
        List<CommentTreeResponse> responses = List.of(
            new CommentTreeResponse(
                100L,
                1L,
                "작성자",
                "comment 1",
                0,
                null,
                100L,
                FIXED_TIME,
                FIXED_TIME,
                null,
                0L,
                0L,
                (short) 0,
                List.of(reply)
            )
        );
        CommentPageResponse<CommentTreeResponse> pageResponse = new CommentPageResponse<>(
            responses,
            0,
            10,
            1,
            1,
            false,
            false
        );
        when(commentService.getArticleComments(10L, 0, 10)).thenReturn(pageResponse);

        // When: 댓글 목록 API 호출
        ResultActions result = mockMvc.perform(get("/api/articles/10/comments?size=10"));

        // Then: 응답 리스트 확인
        result.andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.items[0].id").value(100L))
            .andExpect(jsonPath("$.data.items[0].children[0].id").value(101L));
    }

    // 댓글 스냅샷 조회 API는 페이지 데이터와 syncVersion을 함께 반환해야 한다.
    @Test
    void findSnapshot_returns_page_with_sync_version() throws Exception {
        // Given: 댓글 스냅샷 응답
        CommentTreeResponse response = new CommentTreeResponse(
            100L,
            1L,
            "작성자",
            "comment 1",
            0,
            null,
            100L,
            FIXED_TIME,
            FIXED_TIME,
            null,
            0L,
            0L,
            (short) 0,
            List.of()
        );
        CommentPageResponse<CommentTreeResponse> pageResponse = new CommentPageResponse<>(
            List.of(response),
            0,
            10,
            1,
            1,
            false,
            false
        );
        CommentSnapshotResponse snapshotResponse = new CommentSnapshotResponse(10L, 7L, pageResponse);
        when(commentService.getArticleCommentsSnapshot(10L, 0, 10)).thenReturn(snapshotResponse);

        // When: 댓글 스냅샷 API 호출
        ResultActions result = mockMvc.perform(get("/api/articles/10/comments/snapshot?page=0&size=10"));

        // Then: syncVersion과 페이지 데이터 확인
        result.andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.articleId").value(10L))
            .andExpect(jsonPath("$.data.syncVersion").value(7L))
            .andExpect(jsonPath("$.data.page.items[0].id").value(100L));
    }

    // 댓글 반응 토글 API는 성공 응답을 반환해야 한다.
    @Test
    void toggleReaction_returns_ok() throws Exception {
        // Given: 댓글 반응 요청
        CommentReactionToggleRequest request = new CommentReactionToggleRequest((short) 1);
        CommentReactionSummaryResponse response = new CommentReactionSummaryResponse(
            100L,
            1L,
            0L,
            (short) 1
        );
        when(commentService.toggleReaction(100L, request)).thenReturn(response);

        // When: 댓글 반응 API 호출
        ResultActions result = mockMvc.perform(post("/api/comments/100/reactions")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)));

        // Then: 응답 데이터 확인
        result.andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.commentId").value(100L));
    }

    // 댓글 수정 API는 변경된 응답을 반환해야 한다.
    @Test
    void update_returns_updated_comment() throws Exception {
        // Given: 댓글 수정 요청
        CommentUpdateRequest request = new CommentUpdateRequest("updated comment");
        CommentTreeResponse response = new CommentTreeResponse(
            100L,
            1L,
            "작성자",
            "updated comment",
            0,
            null,
            100L,
            FIXED_TIME,
            FIXED_TIME,
            null,
            0L,
            0L,
            (short) 0,
            List.of()
        );
        when(commentService.update(100L, request)).thenReturn(response);

        // When: 댓글 수정 API 호출
        ResultActions result = mockMvc.perform(put("/api/comments/100")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)));

        // Then: 응답 데이터 확인
        result.andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.content").value("updated comment"));
    }

    // 댓글 삭제 API는 성공 응답을 반환해야 한다.
    @Test
    void delete_returns_ok() throws Exception {
        // Given: 댓글 삭제 준비
        doNothing().when(commentService).delete(100L);

        // When: 댓글 삭제 API 호출
        ResultActions result = mockMvc.perform(delete("/api/comments/100"));

        // Then: 성공 응답 확인
        result.andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data").value(nullValue()));
    }
}
