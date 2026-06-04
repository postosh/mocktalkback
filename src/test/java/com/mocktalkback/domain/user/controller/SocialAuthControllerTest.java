package com.mocktalkback.domain.user.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import tools.jackson.databind.json.JsonMapper;
import com.mocktalkback.domain.realtime.service.NotificationRealtimeTicketService;
import com.mocktalkback.domain.user.dto.AccessTokenResult;
import com.mocktalkback.domain.user.dto.OAuth2CodeRequest;
import com.mocktalkback.domain.user.service.AuthService;
import com.mocktalkback.global.auth.CookieUtil;
import com.mocktalkback.global.auth.OriginAllowlistFilter;
import com.mocktalkback.global.auth.jwt.JwtAccessDeniedHandler;
import com.mocktalkback.global.auth.jwt.JwtAuthEntryPoint;
import com.mocktalkback.global.auth.jwt.JwtSecurityConfig;
import com.mocktalkback.global.auth.jwt.JwtTokenProvider;
import com.mocktalkback.global.auth.jwt.RefreshTokenService;
import com.mocktalkback.global.auth.oauth2.CustomOAuth2UserService;
import com.mocktalkback.global.auth.oauth2.OAuth2LoginFailureHandler;
import com.mocktalkback.global.auth.oauth2.OAuth2LoginSuccessHandler;
import com.mocktalkback.global.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.mocktalkback.support.MocktalkWebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@MocktalkWebMvcTest(controllers = AuthController.class)
@Import({
        SecurityConfig.class,
        JwtSecurityConfig.class,
        JwtTokenProvider.class,
        JwtAuthEntryPoint.class,
        JwtAccessDeniedHandler.class,
        CookieUtil.class,
        OriginAllowlistFilter.class
})
@TestPropertySource(properties = {
        "SERVER_PORT=0",
        "JWT_SECRET=abcdefghijklmnopqrstuvwxyz012345",
        "SECURITY_COOKIE_SECURE=false",
        "SECURITY_ORIGIN_ALLOWLIST=http://localhost:5173"
})
class SocialAuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper objectMapper;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private RefreshTokenService refreshTokenService;

    @MockitoBean
    private CustomOAuth2UserService customOAuth2UserService;

    @MockitoBean
    private OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;

    @MockitoBean
    private OAuth2LoginFailureHandler oAuth2LoginFailureHandler;

    @MockitoBean
    private NotificationRealtimeTicketService notificationRealtimeTicketService;

    @Test
    void oauth2_callback_returns_access_token() throws Exception {
        when(authService.exchangeOAuth2Code("code"))
                .thenReturn(new AccessTokenResult("access-token", 3600));

        OAuth2CodeRequest req = new OAuth2CodeRequest("code");

        var result = mockMvc.perform(post("/api/auth/oauth2/callback")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)));

        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresInSec").value(3600));

        verify(authService).exchangeOAuth2Code("code");
    }
}
