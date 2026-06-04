package com.mocktalkback.dev;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.mocktalkback.support.MocktalkWebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.mocktalkback.domain.role.type.AuthBits;
import com.mocktalkback.domain.role.type.RoleNames;

@MocktalkWebMvcTest(controllers = TestUserRootRoleController.class)
@Import(UserRootRoleTest.TestSecurityConfig.class)
@TestPropertySource(properties = {
        "SERVER_PORT=0",
})
class UserRootRoleTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    // 사용자 역할은 사용자 전용 엔드포인트에 접근할 수 있어야 한다.
    @WithMockUser(roles = "USER")
    void user_role_can_access_user_endpoint() throws Exception {
        // Given

        // When
        ResultActions result = mockMvc.perform(post("/api/dev/user"));

        // Then
        result.andExpect(status().isOk())
                .andExpect(content().string("user"));
    }

    @Test
    // 사용자 역할은 관리자 전용 엔드포인트에 접근할 수 없어야 한다.
    @WithMockUser(roles = "USER")
    void user_role_cannot_access_admin_endpoint() throws Exception {
        // Given

        // When
        ResultActions result = mockMvc.perform(post("/api/dev/admin"));

        // Then
        result.andExpect(status().isForbidden());
    }

    @Test
    // 관리자 역할은 관리자 전용 엔드포인트에 접근할 수 있어야 한다.
    @WithMockUser(roles = "ADMIN")
    void admin_role_can_access_admin_endpoint() throws Exception {
        // Given

        // When
        ResultActions result = mockMvc.perform(post("/api/dev/admin"));

        // Then
        result.andExpect(status().isOk())
                .andExpect(content().string("admin"));
    }

    @Test
    // AuthBits 상수 값이 기대한 비트 마스크인지 검증한다.
    void auth_bits_constants_match_expected_mask() {
        // Given
        int read = AuthBits.READ;
        int write = AuthBits.WRITE;
        int delete = AuthBits.DELETE;
        int admin = AuthBits.ADMIN;

        // When

        // Then
        assertAll(
                () -> assertEquals(1, read),
                () -> assertEquals(2, write),
                () -> assertEquals(4, delete),
                () -> assertEquals(8, admin)
        );
    }

    @Test
    // RoleNames 상수 값이 기대한 문자열인지 검증한다.
    void role_names_constants_match_expected_values() {
        // Given
        String user = RoleNames.USER;
        String writer = RoleNames.WRITER;
        String manager = RoleNames.MANAGER;
        String admin = RoleNames.ADMIN;

        // When

        // Then
        assertAll(
                () -> assertEquals("USER", user),
                () -> assertEquals("WRITER", writer),
                () -> assertEquals("MANAGER", manager),
                () -> assertEquals("ADMIN", admin)
        );
    }

    @TestConfiguration
    @EnableMethodSecurity
    static class TestSecurityConfig {

        @Bean
        SecurityFilterChain testFilterChain(HttpSecurity http) throws Exception {
            http
                    .csrf(csrf -> csrf.disable())
                    .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                    .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
            return http.build();
        }
    }
}
