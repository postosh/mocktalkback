package com.mocktalkback.domain.article.controller;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import com.mocktalkback.domain.article.dto.ArticleCreateRequest;
import com.mocktalkback.domain.article.dto.ArticleBoardResponse;
import com.mocktalkback.domain.article.dto.ArticleBookmarkDeleteRequest;
import com.mocktalkback.domain.article.dto.ArticleBookmarkItemResponse;
import com.mocktalkback.domain.article.dto.ArticleBookmarkStatusResponse;
import com.mocktalkback.domain.article.dto.ArticleDetailResponse;
import com.mocktalkback.domain.article.dto.ArticleEditorDetailResponse;
import com.mocktalkback.domain.article.dto.ArticlePreviewRequest;
import com.mocktalkback.domain.article.dto.ArticlePreviewResponse;
import com.mocktalkback.domain.article.dto.ArticleRecentItemResponse;
import com.mocktalkback.domain.article.dto.ArticleRecommendedItemResponse;
import com.mocktalkback.domain.article.dto.ArticleReactionSummaryResponse;
import com.mocktalkback.domain.article.dto.ArticleReactionToggleRequest;
import com.mocktalkback.domain.article.dto.ArticleResponse;
import com.mocktalkback.domain.article.dto.ArticleTrendingItemResponse;
import com.mocktalkback.domain.article.dto.ArticleUpdateRequest;
import com.mocktalkback.domain.article.type.ArticleContentFormat;
import com.mocktalkback.domain.article.type.ArticleTrendingWindow;
import com.mocktalkback.global.common.dto.PageResponse;
import com.mocktalkback.global.common.dto.SliceResponse;
import com.mocktalkback.domain.article.service.ArticleBookmarkService;
import com.mocktalkback.domain.article.service.ArticleService;
import com.mocktalkback.domain.board.type.BoardVisibility;
import com.mocktalkback.domain.role.type.ContentVisibility;

@MocktalkWebMvcTest(controllers = ArticleController.class)
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = {
    "SERVER_PORT=0"
})
class ArticleControllerTest {

    private static final Instant FIXED_TIME = Instant.parse("2024-01-01T00:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper objectMapper;

    @MockitoBean
    private ArticleService articleService;

    @MockitoBean
    private ArticleBookmarkService articleBookmarkService;

    // 게시글 생성 API는 성공 응답을 반환해야 한다.
    @Test
    void create_returns_ok() throws Exception {
        // Given: 게시글 생성 요청
        ArticleCreateRequest request = new ArticleCreateRequest(
            1L,
            2L,
            3L,
            ContentVisibility.PUBLIC,
            "title",
            "# title",
            ArticleContentFormat.MARKDOWN,
            false,
            List.of()
        );
        ArticleResponse response = new ArticleResponse(
            10L,
            1L,
            2L,
            3L,
            ContentVisibility.PUBLIC,
            "title",
            "content",
            0L,
            false,
            FIXED_TIME,
            FIXED_TIME,
            null
        );
        when(articleService.create(any(ArticleCreateRequest.class))).thenReturn(response);

        // When: 게시글 생성 API 호출
        ResultActions result = mockMvc.perform(post("/api/articles")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)));

        // Then: 응답 데이터 확인
        result.andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.id").value(10L));
    }

    // 공개 인기 게시글 조회 API는 트렌딩 목록을 반환해야 한다.
    @Test
    void findTrendingPublic_returns_items() throws Exception {
        // Given: 공개 인기 게시글 응답
        List<ArticleTrendingItemResponse> response = List.of(
            new ArticleTrendingItemResponse(
                10L,
                1L,
                "free",
                2L,
                "author",
                "title",
                12L,
                3L,
                7L,
                1L,
                18.0d,
                FIXED_TIME
            )
        );
        when(articleService.findTrendingPublic(ArticleTrendingWindow.DAY, 10)).thenReturn(response);

        // When: 공개 인기 게시글 API를 호출하면
        ResultActions result = mockMvc.perform(get("/api/articles/trending"));

        // Then: 트렌딩 목록이 반환되어야 한다.
        result.andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data[0].articleId").value(10L))
            .andExpect(jsonPath("$.data[0].trendScore").value(18.0d));
    }

    // 게시글 단건 조회 API는 응답 데이터를 반환해야 한다.
    @Test
    void findById_returns_article() throws Exception {
        // Given: 게시글 응답
        ArticleBoardResponse boardResponse = new ArticleBoardResponse(
            1L,
            "notice",
            "notice",
            "notice board",
            BoardVisibility.PUBLIC,
            null
        );
        ArticleDetailResponse response = new ArticleDetailResponse(
            10L,
            boardResponse,
            2L,
            "author",
            ContentVisibility.PUBLIC,
            "title",
            "content",
            5L,
            0L,
            0L,
            0L,
            (short) 0,
            false,
            false,
            FIXED_TIME,
            FIXED_TIME,
            List.of()
        );
        when(articleService.findDetailById(10L, "203.0.113.10", "MockBrowser/1.0")).thenReturn(response);

        // When: 게시글 조회 API 호출
        ResultActions result = mockMvc.perform(get("/api/articles/10")
            .header("X-Forwarded-For", "203.0.113.10")
            .header("User-Agent", "MockBrowser/1.0"));

        // Then: 응답 데이터 확인
        result.andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.id").value(10L))
            .andExpect(header().doesNotExist("Set-Cookie"));
    }

    // 게시글 수정용 조회 API는 작성 원본과 포맷을 반환해야 한다.
    @Test
    void findEditorById_returns_editor_payload() throws Exception {
        // Given: 수정용 게시글 응답
        ArticleBoardResponse boardResponse = new ArticleBoardResponse(
            1L,
            "notice",
            "notice",
            "notice board",
            BoardVisibility.PUBLIC,
            null
        );
        ArticleEditorDetailResponse response = new ArticleEditorDetailResponse(
            10L,
            boardResponse,
            2L,
            3L,
            "공지",
            "author",
            ContentVisibility.PUBLIC,
            "title",
            "<h1>title</h1>",
            "# title",
            ArticleContentFormat.MARKDOWN,
            false,
            FIXED_TIME,
            FIXED_TIME,
            List.of()
        );
        when(articleService.findEditorDetailById(10L)).thenReturn(response);

        // When: 수정용 조회 API 호출
        ResultActions result = mockMvc.perform(get("/api/articles/10/editor"));

        // Then: 작성 원본과 포맷이 반환되어야 한다.
        result.andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.contentSource").value("# title"))
            .andExpect(jsonPath("$.data.contentFormat").value("MARKDOWN"));
    }

    // 게시글 미리보기 API는 렌더링된 HTML을 반환해야 한다.
    @Test
    void preview_returns_rendered_html() throws Exception {
        // Given: 미리보기 요청과 응답
        ArticlePreviewRequest request = new ArticlePreviewRequest("# 제목", ArticleContentFormat.MARKDOWN);
        ArticlePreviewResponse response = new ArticlePreviewResponse("<h1>제목</h1>");
        when(articleService.preview(request)).thenReturn(response);

        // When: 미리보기 API 호출
        ResultActions result = mockMvc.perform(post("/api/articles/preview")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)));

        // Then: 렌더링된 HTML이 반환되어야 한다.
        result.andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.content").value("<h1>제목</h1>"));
    }

    // 게시글 목록 조회 API는 리스트 응답을 반환해야 한다.
    @Test
    void findAll_returns_list() throws Exception {
        // Given: 게시글 목록 응답
        List<ArticleResponse> responses = List.of(
            new ArticleResponse(
                10L,
                1L,
                2L,
                3L,
                ContentVisibility.PUBLIC,
                "title",
                "content",
                0L,
                false,
                FIXED_TIME,
                FIXED_TIME,
                null
            ),
            new ArticleResponse(
                11L,
                1L,
                2L,
                null,
                ContentVisibility.PUBLIC,
                "title2",
                "content2",
                0L,
                false,
                FIXED_TIME,
                FIXED_TIME,
                null
            )
        );
        when(articleService.findAll()).thenReturn(responses);

        // When: 게시글 목록 API 호출
        ResultActions result = mockMvc.perform(get("/api/articles"));

        // Then: 응답 리스트 확인
        result.andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data[0].id").value(10L))
            .andExpect(jsonPath("$.data[1].id").value(11L));
    }

    // 홈 최근 공개 게시글 API는 슬라이스 응답을 반환해야 한다.
    @Test
    void findRecentPublic_returns_slice() throws Exception {
        // Given: 홈 최근 공개 게시글 응답
        SliceResponse<ArticleRecentItemResponse> response = new SliceResponse<>(
            List.of(
                new ArticleRecentItemResponse(
                    10L,
                    1L,
                    "free",
                    "자유게시판",
                    2L,
                    "author",
                    "첫 글",
                    "본문 미리보기",
                    3L,
                    7L,
                    12L,
                    FIXED_TIME
                )
            ),
            0,
            8,
            true,
            false
        );
        when(articleService.findRecentPublic(0, 8)).thenReturn(response);

        // When: 홈 최근 공개 게시글 API 호출
        ResultActions result = mockMvc.perform(get("/api/articles/recent")
            .param("page", "0")
            .param("size", "8"));

        // Then: 슬라이스 응답 확인
        result.andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.items[0].boardSlug").value("free"))
            .andExpect(jsonPath("$.data.items[0].previewText").value("본문 미리보기"))
            .andExpect(jsonPath("$.data.hasNext").value(true));
    }

    // 홈 추천 게시글 API는 추천 목록을 반환해야 한다.
    @Test
    void findRecommendedPublic_returns_items() throws Exception {
        // Given: 추천 게시글 응답
        List<ArticleRecommendedItemResponse> response = List.of(
            new ArticleRecommendedItemResponse(
                21L,
                1L,
                "free",
                "자유게시판",
                2L,
                "author",
                "추천 글",
                18L,
                4L,
                9L,
                1L,
                14.5d,
                "북마크한 글과 비슷한 게시판 기반",
                true,
                FIXED_TIME
            )
        );
        when(articleService.findRecommendedPublic(9)).thenReturn(response);

        // When: 홈 추천 게시글 API 호출
        ResultActions result = mockMvc.perform(get("/api/articles/recommended")
            .param("limit", "9"));

        // Then: 추천 목록이 반환되어야 한다.
        result.andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data[0].articleId").value(21L))
            .andExpect(jsonPath("$.data[0].recommendationReason").value("북마크한 글과 비슷한 게시판 기반"))
            .andExpect(jsonPath("$.data[0].personalized").value(true));
    }

    // 게시글 반응 토글 API는 성공 응답을 반환해야 한다.
    @Test
    void toggleReaction_returns_ok() throws Exception {
        // Given: 반응 토글 요청
        ArticleReactionToggleRequest request = new ArticleReactionToggleRequest((short) 1);
        ArticleReactionSummaryResponse response = new ArticleReactionSummaryResponse(
            10L,
            1L,
            0L,
            (short) 1
        );
        when(articleService.toggleReaction(10L, request)).thenReturn(response);

        // When: 반응 토글 API 호출
        ResultActions result = mockMvc.perform(post("/api/articles/10/reactions")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)));

        // Then: 응답 데이터 확인
        result.andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.articleId").value(10L));
    }

    // 게시글 북마크 API는 성공 응답을 반환해야 한다.
    @Test
    void bookmark_returns_ok() throws Exception {
        // Given: 북마크 응답
        ArticleBookmarkStatusResponse response = new ArticleBookmarkStatusResponse(10L, true);
        when(articleService.bookmark(10L)).thenReturn(response);

        // When: 북마크 API 호출
        ResultActions result = mockMvc.perform(post("/api/articles/10/bookmark"));

        // Then: 응답 데이터 확인
        result.andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.bookmarked").value(true));
    }

    // 게시글 북마크 해제 API는 성공 응답을 반환해야 한다.
    @Test
    void unbookmark_returns_ok() throws Exception {
        // Given: 북마크 해제 응답
        ArticleBookmarkStatusResponse response = new ArticleBookmarkStatusResponse(10L, false);
        when(articleService.unbookmark(10L)).thenReturn(response);

        // When: 북마크 해제 API 호출
        ResultActions result = mockMvc.perform(delete("/api/articles/10/bookmark"));

        // Then: 응답 데이터 확인
        result.andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.bookmarked").value(false));
    }

    // 북마크 목록 조회 API는 리스트 응답을 반환해야 한다.
    @Test
    void findBookmarks_returns_list() throws Exception {
        // Given: 북마크 목록 응답
        List<ArticleBookmarkItemResponse> items = List.of(
            new ArticleBookmarkItemResponse(
                10L,
                1L,
                "notice",
                2L,
                "author",
                "title",
                0L,
                0L,
                0L,
                0L,
                false,
                FIXED_TIME
            )
        );
        PageResponse<ArticleBookmarkItemResponse> pageResponse = new PageResponse<>(
            items,
            0,
            10,
            1,
            1,
            false,
            false
        );
        when(articleBookmarkService.findMyBookmarks(0, 10)).thenReturn(pageResponse);

        // When: 북마크 목록 API 호출
        ResultActions result = mockMvc.perform(get("/api/articles/bookmarks")
            .param("page", "0")
            .param("size", "10"));

        // Then: 응답 데이터 확인
        result.andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.items[0].id").value(10L));
    }

    // 북마크 선택 삭제 API는 성공 응답을 반환해야 한다.
    @Test
    void deleteBookmarks_returns_ok() throws Exception {
        // Given: 삭제 요청
        ArticleBookmarkDeleteRequest request = new ArticleBookmarkDeleteRequest(List.of(10L, 11L));
        doNothing().when(articleBookmarkService).deleteByArticleIds(request);

        // When: 북마크 삭제 API 호출
        ResultActions result = mockMvc.perform(post("/api/articles/bookmarks/delete")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)));

        // Then: 성공 응답 확인
        result.andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data").value(nullValue()));
    }

    // 북마크 전체 삭제 API는 성공 응답을 반환해야 한다.
    @Test
    void deleteAllBookmarks_returns_ok() throws Exception {
        // Given: 전체 삭제 준비
        doNothing().when(articleBookmarkService).deleteAllByUser();

        // When: 북마크 전체 삭제 API 호출
        ResultActions result = mockMvc.perform(delete("/api/articles/bookmarks"));

        // Then: 성공 응답 확인
        result.andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data").value(nullValue()));
    }

    // 게시글 수정 API는 변경된 응답을 반환해야 한다.
    @Test
    void update_returns_updated_article() throws Exception {
        // Given: 게시글 수정 요청
        ArticleUpdateRequest request = new ArticleUpdateRequest(
            3L,
            ContentVisibility.MEMBERS,
            "updated title",
            "## updated content",
            ArticleContentFormat.MARKDOWN,
            true,
            List.of()
        );
        ArticleResponse response = new ArticleResponse(
            10L,
            1L,
            2L,
            3L,
            ContentVisibility.MEMBERS,
            "updated title",
            "updated content",
            0L,
            true,
            FIXED_TIME,
            FIXED_TIME,
            null
        );
        when(articleService.update(10L, request)).thenReturn(response);

        // When: 게시글 수정 API 호출
        ResultActions result = mockMvc.perform(put("/api/articles/10")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)));

        // Then: 응답 데이터 확인
        result.andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.title").value("updated title"));
    }

    // 게시글 삭제 API는 성공 응답을 반환해야 한다.
    @Test
    void delete_returns_ok() throws Exception {
        // Given: 게시글 삭제 준비
        doNothing().when(articleService).delete(10L);

        // When: 게시글 삭제 API 호출
        ResultActions result = mockMvc.perform(delete("/api/articles/10"));

        // Then: 성공 응답 확인
        result.andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data").value(nullValue()));
    }

    // 게시글 첨부파일 다운로드 API는 저장소 URL로 리다이렉트해야 한다.
    @Test
    void downloadAttachment_returns_redirect_location() throws Exception {
        // Given: 첨부파일 다운로드 URL
        when(articleService.resolveAttachmentDownloadLocation(10L, 20L))
            .thenReturn("https://storage.mocktalk.local/file.zip");

        // When: 첨부파일 다운로드 API 호출
        ResultActions result = mockMvc.perform(get("/api/articles/10/attachments/20/download"));

        // Then: 리다이렉트 위치를 반환한다.
        result.andExpect(status().isFound())
            .andExpect(header().string("Location", "https://storage.mocktalk.local/file.zip"));
    }
}
