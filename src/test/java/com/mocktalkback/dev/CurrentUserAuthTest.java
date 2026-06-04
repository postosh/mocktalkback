package com.mocktalkback.dev;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.mocktalkback.support.MocktalkWebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@MocktalkWebMvcTest(controllers = TestCurrentUserController.class)
@Import(CurrentUserAuthTest.TestSecurityConfig.class)
@TestPropertySource(properties = "SERVER_PORT=0")
class CurrentUserAuthTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void returns_user_id_and_role_from_authentication() throws Exception {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                42L,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );

        mockMvc.perform(get("/api/dev/me").with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(42))
                .andExpect(jsonPath("$.role").value("ROLE_ADMIN"));
    }

    @Test
    void rejects_when_not_authenticated() throws Exception {
        mockMvc.perform(get("/api/dev/me"))
                .andExpect(status().isForbidden());
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
