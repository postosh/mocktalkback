package com.mocktalkback.domain.moderation.controller;

import static org.hamcrest.Matchers.nullValue;
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
import com.mocktalkback.domain.article.dto.ArticleCategoryResponse;
import com.mocktalkback.domain.moderation.dto.BoardCategoryCreateRequest;
import com.mocktalkback.domain.moderation.dto.BoardCategoryUpdateRequest;
import com.mocktalkback.domain.moderation.service.BoardCategoryAdminService;

@MocktalkWebMvcTest(controllers = BoardCategoryAdminController.class)
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = {
    "SERVER_PORT=0"
})
class BoardCategoryAdminControllerTest {

    private static final Instant FIXED_TIME = Instant.parse("2024-01-01T00:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper objectMapper;

    @MockitoBean
    private BoardCategoryAdminService boardCategoryAdminService;

    // 게시판 카테고리 목록 API는 리스트 응답을 반환해야 한다.
    @Test
    void getCategories_returns_list() throws Exception {
        // Given: 카테고리 목록 응답
        ArticleCategoryResponse item = new ArticleCategoryResponse(
            1L,
            2L,
            "자유",
            FIXED_TIME,
            FIXED_TIME
        );
        when(boardCategoryAdminService.findAll(2L)).thenReturn(List.of(item));

        // When: 카테고리 목록 API 호출
        ResultActions result = mockMvc.perform(get("/api/boards/2/admin/categories"));

        // Then: 응답 데이터 확인
        result.andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data[0].categoryName").value("자유"));
    }

    // 게시판 카테고리 생성 API는 응답을 반환해야 한다.
    @Test
    void createCategory_returns_item() throws Exception {
        // Given: 카테고리 생성 요청/응답
        BoardCategoryCreateRequest request = new BoardCategoryCreateRequest("공지");
        ArticleCategoryResponse response = new ArticleCategoryResponse(
            2L,
            2L,
            "공지",
            FIXED_TIME,
            FIXED_TIME
        );
        when(boardCategoryAdminService.create(eq(2L), any(BoardCategoryCreateRequest.class))).thenReturn(response);

        // When: 카테고리 생성 API 호출
        ResultActions result = mockMvc.perform(post("/api/boards/2/admin/categories")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)));

        // Then: 응답 데이터 확인
        result.andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.id").value(2L));
    }

    // 게시판 카테고리 수정 API는 응답을 반환해야 한다.
    @Test
    void updateCategory_returns_item() throws Exception {
        // Given: 카테고리 수정 요청/응답
        BoardCategoryUpdateRequest request = new BoardCategoryUpdateRequest("이벤트");
        ArticleCategoryResponse response = new ArticleCategoryResponse(
            3L,
            2L,
            "이벤트",
            FIXED_TIME,
            FIXED_TIME
        );
        when(boardCategoryAdminService.update(eq(2L), eq(3L), any(BoardCategoryUpdateRequest.class))).thenReturn(response);

        // When: 카테고리 수정 API 호출
        ResultActions result = mockMvc.perform(put("/api/boards/2/admin/categories/3")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)));

        // Then: 응답 데이터 확인
        result.andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.categoryName").value("이벤트"));
    }

    // 게시판 카테고리 삭제 API는 성공 응답을 반환해야 한다.
    @Test
    void deleteCategory_returns_ok() throws Exception {
        // Given: 삭제 처리
        doNothing().when(boardCategoryAdminService).delete(2L, 3L);

        // When: 카테고리 삭제 API 호출
        ResultActions result = mockMvc.perform(delete("/api/boards/2/admin/categories/3"));

        // Then: 응답 데이터 확인
        result.andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data").value(nullValue()));
    }
}
