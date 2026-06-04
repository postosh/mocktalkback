package com.mocktalkback.domain.moderation.controller;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import tools.jackson.databind.json.JsonMapper;
import com.mocktalkback.domain.moderation.dto.AdminUserListItemResponse;
import com.mocktalkback.domain.moderation.dto.AdminUserRoleUpdateRequest;
import com.mocktalkback.domain.moderation.service.AdminUserService;
import com.mocktalkback.domain.moderation.type.AdminUserStatus;
import com.mocktalkback.global.common.dto.PageResponse;

@MocktalkWebMvcTest(controllers = AdminUserController.class)
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = {
    "SERVER_PORT=0"
})
@WithMockUser(roles = "ADMIN")
class AdminUserControllerTest {

    private static final Instant FIXED_TIME = Instant.parse("2024-01-01T00:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper objectMapper;

    @MockitoBean
    private AdminUserService adminUserService;

    // 관리자 사용자 목록 API는 페이지 응답을 반환해야 한다.
    @Test
    void getUsers_returns_page() throws Exception {
        // Given: 사용자 목록 응답
        AdminUserListItemResponse item = new AdminUserListItemResponse(
            1L,
            "admin",
            "admin@example.com",
            "Admin",
            "관리자",
            "admin",
            "ADMIN",
            true,
            false,
            FIXED_TIME,
            FIXED_TIME
        );
        PageResponse<AdminUserListItemResponse> response = new PageResponse<>(
            List.of(item),
            0,
            10,
            1,
            1,
            false,
            false
        );
        when(adminUserService.search(eq(AdminUserStatus.ACTIVE), eq("admin"), eq(0), eq(10))).thenReturn(response);

        // When: 사용자 목록 API 호출
        ResultActions result = mockMvc.perform(get("/api/admin/users")
            .param("status", "ACTIVE")
            .param("keyword", "admin")
            .param("page", "0")
            .param("size", "10"));

        // Then: 응답 데이터 확인
        result.andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.items[0].loginId").value("admin"));
    }

    // 관리자 사용자 잠금 API는 응답을 반환해야 한다.
    @Test
    void lockUser_returns_item() throws Exception {
        // Given: 잠금 처리 응답
        AdminUserListItemResponse item = new AdminUserListItemResponse(
            2L,
            "user01",
            "user01@example.com",
            "User",
            "유저",
            "user01",
            "USER",
            true,
            true,
            FIXED_TIME,
            FIXED_TIME
        );
        when(adminUserService.lock(2L)).thenReturn(item);

        // When: 사용자 잠금 API 호출
        ResultActions result = mockMvc.perform(put("/api/admin/users/2/lock"));

        // Then: 응답 데이터 확인
        result.andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.locked").value(true));
    }

    // 관리자 사용자 잠금 해제 API는 응답을 반환해야 한다.
    @Test
    void unlockUser_returns_item() throws Exception {
        // Given: 잠금 해제 응답
        AdminUserListItemResponse item = new AdminUserListItemResponse(
            2L,
            "user01",
            "user01@example.com",
            "User",
            "유저",
            "user01",
            "USER",
            true,
            false,
            FIXED_TIME,
            FIXED_TIME
        );
        when(adminUserService.unlock(2L)).thenReturn(item);

        // When: 사용자 잠금 해제 API 호출
        ResultActions result = mockMvc.perform(put("/api/admin/users/2/unlock"));

        // Then: 응답 데이터 확인
        result.andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.locked").value(false));
    }

    // 관리자 사용자 권한 변경 API는 응답을 반환해야 한다.
    @Test
    void updateRole_returns_item() throws Exception {
        // Given: 권한 변경 요청/응답
        AdminUserRoleUpdateRequest request = new AdminUserRoleUpdateRequest("MANAGER");
        AdminUserListItemResponse item = new AdminUserListItemResponse(
            3L,
            "user02",
            "user02@example.com",
            "User",
            "유저2",
            "user02",
            "MANAGER",
            true,
            false,
            FIXED_TIME,
            FIXED_TIME
        );
        when(adminUserService.changeRole(eq(3L), any(String.class))).thenReturn(item);

        // When: 권한 변경 API 호출
        ResultActions result = mockMvc.perform(put("/api/admin/users/3/role")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)));

        // Then: 응답 데이터 확인
        result.andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.roleName").value("MANAGER"))
            .andExpect(jsonPath("$.error").value(nullValue()));
    }
}
