package com.mocktalkback.domain.moderation.controller;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
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
import com.mocktalkback.domain.board.type.BoardRole;
import com.mocktalkback.domain.moderation.dto.BoardMemberListItemResponse;
import com.mocktalkback.domain.moderation.dto.BoardMemberRoleUpdateRequest;
import com.mocktalkback.domain.moderation.dto.BoardMemberStatusRequest;
import com.mocktalkback.domain.moderation.service.BoardMemberAdminService;
import com.mocktalkback.global.common.dto.PageResponse;

@MocktalkWebMvcTest(controllers = BoardMemberAdminController.class)
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = {
    "SERVER_PORT=0"
})
class BoardMemberAdminControllerTest {

    private static final Instant FIXED_TIME = Instant.parse("2024-01-01T00:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper objectMapper;

    @MockitoBean
    private BoardMemberAdminService boardMemberAdminService;

    // 게시판 멤버 목록 API는 페이지 응답을 반환해야 한다.
    @Test
    void getMembers_returns_page() throws Exception {
        // Given: 멤버 목록 응답
        BoardMemberListItemResponse item = new BoardMemberListItemResponse(
            10L,
            3L,
            "user01",
            "홍길동",
            "hong",
            BoardRole.PENDING,
            null,
            null,
            FIXED_TIME,
            FIXED_TIME
        );
        PageResponse<BoardMemberListItemResponse> response = new PageResponse<>(
            List.of(item),
            0,
            10,
            1,
            1,
            false,
            false
        );
        when(boardMemberAdminService.findMembers(eq(2L), eq(BoardRole.PENDING), eq(0), eq(10))).thenReturn(response);

        // When: 멤버 목록 API 호출
        ResultActions result = mockMvc.perform(get("/api/boards/2/admin/members")
            .param("status", "PENDING")
            .param("page", "0")
            .param("size", "10"));

        // Then: 응답 데이터 확인
        result.andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.items[0].boardRole").value("PENDING"));
    }

    // 게시판 멤버 승인 API는 응답을 반환해야 한다.
    @Test
    void approve_returns_item() throws Exception {
        // Given: 승인 처리 응답
        BoardMemberListItemResponse item = new BoardMemberListItemResponse(
            10L,
            3L,
            "user01",
            "홍길동",
            "hong",
            BoardRole.MEMBER,
            1L,
            "관리자",
            FIXED_TIME,
            FIXED_TIME
        );
        when(boardMemberAdminService.approve(2L, 10L)).thenReturn(item);

        // When: 멤버 승인 API 호출
        ResultActions result = mockMvc.perform(put("/api/boards/2/admin/members/10/approve"));

        // Then: 응답 데이터 확인
        result.andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.boardRole").value("MEMBER"));
    }

    // 게시판 멤버 거절 API는 성공 응답을 반환해야 한다.
    @Test
    void reject_returns_ok() throws Exception {
        // Given: 거절 처리
        doNothing().when(boardMemberAdminService).reject(2L, 10L);

        // When: 멤버 거절 API 호출
        ResultActions result = mockMvc.perform(put("/api/boards/2/admin/members/10/reject"));

        // Then: 응답 데이터 확인
        result.andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data").value(nullValue()));
    }

    // 게시판 멤버 역할 변경 API는 응답을 반환해야 한다.
    @Test
    void changeRole_returns_item() throws Exception {
        // Given: 역할 변경 요청/응답
        BoardMemberRoleUpdateRequest request = new BoardMemberRoleUpdateRequest(BoardRole.MODERATOR);
        BoardMemberListItemResponse item = new BoardMemberListItemResponse(
            10L,
            3L,
            "user01",
            "홍길동",
            "hong",
            BoardRole.MODERATOR,
            1L,
            "관리자",
            FIXED_TIME,
            FIXED_TIME
        );
        when(boardMemberAdminService.changeRole(eq(2L), eq(10L), eq(BoardRole.MODERATOR))).thenReturn(item);

        // When: 멤버 역할 변경 API 호출
        ResultActions result = mockMvc.perform(put("/api/boards/2/admin/members/10/role")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)));

        // Then: 응답 데이터 확인
        result.andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.boardRole").value("MODERATOR"));
    }

    // 게시판 멤버 상태 변경 API는 응답을 반환해야 한다.
    @Test
    void changeStatus_returns_item() throws Exception {
        // Given: 상태 변경 요청/응답
        BoardMemberStatusRequest request = new BoardMemberStatusRequest(BoardRole.BANNED);
        BoardMemberListItemResponse item = new BoardMemberListItemResponse(
            10L,
            3L,
            "user01",
            "홍길동",
            "hong",
            BoardRole.BANNED,
            1L,
            "관리자",
            FIXED_TIME,
            FIXED_TIME
        );
        when(boardMemberAdminService.changeStatus(eq(2L), eq(10L), eq(BoardRole.BANNED))).thenReturn(item);

        // When: 멤버 상태 변경 API 호출
        ResultActions result = mockMvc.perform(put("/api/boards/2/admin/members/10/status")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)));

        // Then: 응답 데이터 확인
        result.andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.boardRole").value("BANNED"));
    }
}
