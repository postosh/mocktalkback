package com.mocktalkback.domain.search.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import com.mocktalkback.support.MocktalkWebMvcTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.mocktalkback.domain.board.type.BoardVisibility;
import com.mocktalkback.domain.search.dto.ArticleSearchResponse;
import com.mocktalkback.domain.search.dto.BoardSearchResponse;
import com.mocktalkback.domain.search.dto.CommentSearchResponse;
import com.mocktalkback.domain.search.dto.SearchResponse;
import com.mocktalkback.domain.search.dto.UserSearchResponse;
import com.mocktalkback.domain.search.service.SearchService;
import com.mocktalkback.domain.search.type.SearchType;
import com.mocktalkback.global.common.dto.SliceResponse;
import com.mocktalkback.global.common.type.SortOrder;

@MocktalkWebMvcTest(controllers = SearchController.class)
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = {
    "SERVER_PORT=0"
})
class SearchControllerTest {

    private static final Instant FIXED_TIME = Instant.parse("2024-01-01T00:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SearchService searchService;

    // 통합 검색 API는 검색 결과를 반환해야 한다.
    @Test
    void search_returns_results() throws Exception {
        // Given: 검색 응답 데이터
        SliceResponse<BoardSearchResponse> boardPage = new SliceResponse<>(
            List.of(new BoardSearchResponse(
                1L,
                "공지사항",
                "notice",
                "공지 게시판",
                BoardVisibility.PUBLIC,
                null,
                FIXED_TIME
            )),
            0,
            10,
            false,
            false
        );
        SliceResponse<ArticleSearchResponse> articlePage = new SliceResponse<>(
            List.of(new ArticleSearchResponse(
                10L,
                1L,
                "notice",
                "공지사항",
                3L,
                "관리자",
                "테스트 글",
                5L,
                "업데이트",
                0,
                0,
                0,
                0,
                false,
                FIXED_TIME
            )),
            0,
            10,
            false,
            false
        );
        SliceResponse<CommentSearchResponse> commentPage = new SliceResponse<>(
            List.of(new CommentSearchResponse(
                100L,
                10L,
                "테스트 글",
                1L,
                "notice",
                "공지사항",
                3L,
                "관리자",
                "댓글 내용",
                FIXED_TIME
            )),
            0,
            10,
            false,
            false
        );
        SliceResponse<UserSearchResponse> userPage = new SliceResponse<>(
            List.of(new UserSearchResponse(
                3L,
                "admin",
                "관리자",
                FIXED_TIME
            )),
            0,
            10,
            false,
            false
        );
        SearchResponse response = new SearchResponse(boardPage, articlePage, commentPage, userPage);
        when(searchService.search(eq("공지"), eq(SearchType.ALL), eq(SortOrder.LATEST), eq(0), eq(10), isNull()))
            .thenReturn(response);

        // When: 통합 검색 호출
        ResultActions result = mockMvc.perform(get("/api/search")
            .param("q", "공지")
            .param("type", "ALL")
            .param("page", "0")
            .param("size", "10")
            .param("order", "LATEST"));

        // Then: 응답 데이터 확인
        result.andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.boards.items[0].slug").value("notice"))
            .andExpect(jsonPath("$.data.articles.items[0].title").value("테스트 글"))
            .andExpect(jsonPath("$.data.articles.items[0].categoryName").value("업데이트"))
            .andExpect(jsonPath("$.data.comments.items[0].content").value("댓글 내용"))
            .andExpect(jsonPath("$.data.users.items[0].handle").value("admin"));
    }

    // 게시판 필터 검색은 boardSlug 파라미터를 전달해야 한다.
    @Test
    void search_with_board_slug_returns_articles() throws Exception {
        // Given: 게시판 검색 결과
        SliceResponse<ArticleSearchResponse> articlePage = new SliceResponse<>(
            List.of(new ArticleSearchResponse(
                10L,
                1L,
                "notice",
                "공지사항",
                3L,
                "관리자",
                "공지 제목",
                9L,
                "공지",
                0,
                0,
                0,
                0,
                false,
                FIXED_TIME
            )),
            0,
            10,
            false,
            false
        );
        SearchResponse response = new SearchResponse(
            new SliceResponse<>(List.of(), 0, 10, false, false),
            articlePage,
            new SliceResponse<>(List.of(), 0, 10, false, false),
            new SliceResponse<>(List.of(), 0, 10, false, false)
        );
        when(searchService.search(eq("공지"), eq(SearchType.ARTICLE), eq(SortOrder.LATEST), eq(0), eq(10), eq("notice")))
            .thenReturn(response);

        // When: 게시글 검색 호출
        ResultActions result = mockMvc.perform(get("/api/search")
            .param("q", "공지")
            .param("type", "ARTICLE")
            .param("page", "0")
            .param("size", "10")
            .param("boardSlug", "notice")
            .param("order", "LATEST"));

        // Then: 응답 데이터 확인
        result.andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.articles.items[0].boardSlug").value("notice"));
    }
}
