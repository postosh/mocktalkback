package com.mocktalkback.domain.moderation.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.nullValue;

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
import com.mocktalkback.domain.board.dto.BoardResponse;
import com.mocktalkback.domain.board.type.BoardArticleWritePolicy;
import com.mocktalkback.domain.board.type.BoardVisibility;
import com.mocktalkback.domain.moderation.dto.AdminBoardCreateRequest;
import com.mocktalkback.domain.moderation.dto.AdminBoardUpdateRequest;
import com.mocktalkback.domain.moderation.service.AdminBoardService;
import com.mocktalkback.domain.moderation.type.AdminBoardSortBy;
import com.mocktalkback.global.common.dto.PageResponse;

@MocktalkWebMvcTest(controllers = AdminBoardController.class)
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = {
    "SERVER_PORT=0"
})
@WithMockUser(roles = "ADMIN")
class AdminBoardControllerTest {

    private static final Instant FIXED_TIME = Instant.parse("2024-01-01T00:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper objectMapper;

    @MockitoBean
    private AdminBoardService adminBoardService;

    // 게시판 목록 조회 API는 페이지 응답을 반환해야 한다.
    @Test
    void getBoards_returns_page() throws Exception {
        // Given: 게시판 목록 응답
        BoardResponse board = new BoardResponse(
            1L,
            "notice",
            "notice",
            "notice board",
            BoardVisibility.PUBLIC,
            BoardArticleWritePolicy.ALL_AUTHENTICATED,
            FIXED_TIME,
            FIXED_TIME,
            null,
            null
        );
        PageResponse<BoardResponse> response = new PageResponse<>(
            List.of(board),
            0,
            10,
            1,
            1,
            false,
            false
        );
        when(adminBoardService.findBoards(
            eq("notice"),
            eq(BoardVisibility.PUBLIC),
            eq(true),
            eq(AdminBoardSortBy.UPDATED_AT),
            eq(true),
            eq(0),
            eq(10)
        )).thenReturn(response);

        // When: 게시판 목록 API 호출
        ResultActions result = mockMvc.perform(get("/api/admin/boards")
            .param("keyword", "notice")
            .param("visibility", "PUBLIC")
            .param("includeDeleted", "true")
            .param("sort", "ASC")
            .param("sortBy", "UPDATED_AT")
            .param("page", "0")
            .param("size", "10"));

        // Then: 응답 데이터 확인
        result.andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.items[0].slug").value("notice"));
    }

    // 게시판 생성 API는 생성 응답을 반환해야 한다.
    @Test
    void create_returns_board() throws Exception {
        // Given: 게시판 생성 요청/응답
        AdminBoardCreateRequest request = new AdminBoardCreateRequest(
            "공지사항",
            "notice",
            "notice board",
            BoardVisibility.PUBLIC
        );
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
        when(adminBoardService.create(any(AdminBoardCreateRequest.class))).thenReturn(response);

        // When: 게시판 생성 API 호출
        ResultActions result = mockMvc.perform(post("/api/admin/boards")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)));

        // Then: 응답 데이터 확인
        result.andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.id").value(2L));
    }

    // 게시판 수정 API는 수정 응답을 반환해야 한다.
    @Test
    void update_returns_board() throws Exception {
        // Given: 게시판 수정 요청/응답
        AdminBoardUpdateRequest request = new AdminBoardUpdateRequest(
            "공지사항",
            "notice",
            "notice board",
            BoardVisibility.PUBLIC
        );
        BoardResponse response = new BoardResponse(
            1L,
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
        when(adminBoardService.update(eq(1L), any(AdminBoardUpdateRequest.class))).thenReturn(response);

        // When: 게시판 수정 API 호출
        ResultActions result = mockMvc.perform(put("/api/admin/boards/1")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)));

        // Then: 응답 데이터 확인
        result.andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.slug").value("notice"));
    }

    // 게시판 삭제 API는 성공 응답을 반환해야 한다.
    @Test
    void delete_returns_ok() throws Exception {
        // Given: 삭제 처리
        doNothing().when(adminBoardService).delete(1L);

        // When: 게시판 삭제 API 호출
        ResultActions result = mockMvc.perform(delete("/api/admin/boards/1"));

        // Then: 응답 데이터 확인
        result.andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data").value(nullValue()));
    }

    // 게시판 대표 이미지 삭제 API는 응답을 반환해야 한다.
    @Test
    void deleteImage_returns_board() throws Exception {
        // Given: 이미지 삭제 응답
        BoardResponse response = new BoardResponse(
            1L,
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
        when(adminBoardService.deleteBoardImage(1L)).thenReturn(response);

        // When: 게시판 대표 이미지 삭제 API 호출
        ResultActions result = mockMvc.perform(delete("/api/admin/boards/1/image"));

        // Then: 응답 데이터 확인
        result.andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.id").value(1L));
    }
}
