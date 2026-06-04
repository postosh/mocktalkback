package com.mocktalkback.domain.user.controller;


import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.mocktalkback.global.i18n.ApiException;

import com.mocktalkback.domain.user.dto.AccessTokenResult;
import com.mocktalkback.domain.user.dto.AuthTokens;
import com.mocktalkback.domain.user.dto.JoinRequest;
import com.mocktalkback.domain.user.dto.LoginRequest;
import com.mocktalkback.domain.user.dto.OAuth2CodeRequest;
import com.mocktalkback.domain.user.dto.RefreshTokens;
import com.mocktalkback.domain.user.dto.TokenResponse;
import com.mocktalkback.domain.user.service.AuthService;
import com.mocktalkback.global.auth.CookieUtil;
import com.mocktalkback.global.auth.jwt.RefreshTokenService;
import com.mocktalkback.global.common.dto.ApiEnvelope;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
@Tag(name = "Auth", description = "로그인/회원가입/토큰 갱신 API")
public class AuthController {

    private final AuthService userService;
    private final CookieUtil cookieUtil;
    private final RefreshTokenService refreshTokenService;

    @PostMapping("/auth/join")
    @SecurityRequirements
    @Operation(summary = "회원가입", description = "로컬 계정 회원가입")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "가입 성공",
                    content = @Content(schema = @Schema(implementation = ApiEnvelope.class))
            ),
            @ApiResponse(responseCode = "400", description = "요청 값 오류")
    })
    public ResponseEntity<ApiEnvelope<Void>> join(@Valid @RequestBody JoinRequest joinDto) {
        userService.join(joinDto);
        return ResponseEntity.ok(ApiEnvelope.ok());
    }

    @PostMapping("/auth/login")
    @SecurityRequirements
    @Operation(summary = "로그인", description = "아이디/비밀번호로 로그인하고 Access Token을 발급합니다.")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "로그인 성공",
                    content = @Content(schema = @Schema(implementation = TokenResponse.class))
            ),
            @ApiResponse(responseCode = "401", description = "인증 실패")
    })
    public ResponseEntity<TokenResponse> login(@RequestBody @Valid LoginRequest req) {
        AuthTokens tokens = userService.login(req);
        ResponseCookie refreshCookie = req.rememberMe()
                ? cookieUtil.create(tokens.refreshToken(), tokens.refreshExpiresInSec())
                : cookieUtil.createSession(tokens.refreshToken());
        ResponseCookie logoutCookie = req.rememberMe()
                ? cookieUtil.createLogout(tokens.refreshToken(), tokens.refreshExpiresInSec())
                : cookieUtil.createLogoutSession(tokens.refreshToken());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                .header(HttpHeaders.SET_COOKIE, logoutCookie.toString())
                .body(new TokenResponse(tokens.accessToken(), "Bearer", tokens.accessExpiresInSec()));
    }
    
    @PostMapping("/auth/refresh")
    @SecurityRequirements
    @Operation(summary = "토큰 갱신", description = "Refresh 쿠키로 Access Token을 재발급합니다.")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "갱신 성공",
                    content = @Content(schema = @Schema(implementation = TokenResponse.class))
            ),
            @ApiResponse(responseCode = "401", description = "Refresh 토큰 만료/무효")
    })
    public ResponseEntity<TokenResponse> refresh(
            @Parameter(
                    in = ParameterIn.COOKIE,
                    name = CookieUtil.COOKIE_NAME,
                    description = "HttpOnly Refresh Token 쿠키"
            )
            @CookieValue(value = CookieUtil.COOKIE_NAME, required = false) String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .header(HttpHeaders.SET_COOKIE, cookieUtil.clear().toString())
                .header(HttpHeaders.SET_COOKIE, cookieUtil.clearLogout().toString())
                .build();
        }

        try {
            RefreshTokens tokens = userService.refresh(refreshToken);

            // 새 refresh 쿠키로 회전
            ResponseCookie refreshCookie = tokens.rememberMe()
                    ? cookieUtil.create(tokens.refreshToken(), tokens.refreshExpiresInSec())
                    : cookieUtil.createSession(tokens.refreshToken());
            ResponseCookie logoutCookie = tokens.rememberMe()
                    ? cookieUtil.createLogout(tokens.refreshToken(), tokens.refreshExpiresInSec())
                    : cookieUtil.createLogoutSession(tokens.refreshToken());

            return ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                    .header(HttpHeaders.SET_COOKIE, logoutCookie.toString())
                    .body(new TokenResponse(tokens.accessToken(), "Bearer", tokens.accessExpiresInSec()));
        } catch (ApiException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .header(HttpHeaders.SET_COOKIE, cookieUtil.clear().toString())
                    .header(HttpHeaders.SET_COOKIE, cookieUtil.clearLogout().toString())
                    .build();
        }
    }

    @PostMapping("/auth/logout")
    @Operation(summary = "로그아웃", description = "Refresh 토큰을 폐기하고 쿠키를 삭제합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "로그아웃 성공")
    })
    public ResponseEntity<Void> logout(
            @Parameter(
                    in = ParameterIn.COOKIE,
                    name = CookieUtil.COOKIE_NAME,
                    description = "HttpOnly Refresh Token 쿠키"
            )
            @CookieValue(value = CookieUtil.COOKIE_NAME, required = false) String refreshToken
    ) {
        if (refreshToken != null && !refreshToken.isBlank()) {
            try {
                refreshTokenService.revoke(refreshToken);
            } catch (ApiException ignored) {
            }
        }
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookieUtil.clear().toString())
                .header(HttpHeaders.SET_COOKIE, cookieUtil.clearLogout().toString())
                .build();
    }

    @PostMapping("/auth/oauth2/callback")
    @SecurityRequirements
    @Operation(summary = "OAuth2 코드 교환", description = "1회용 코드를 Access Token으로 교환합니다.")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "교환 성공",
                    content = @Content(schema = @Schema(implementation = TokenResponse.class))
            ),
            @ApiResponse(responseCode = "401", description = "코드 만료/무효")
    })
    public ResponseEntity<TokenResponse> oauth2Callback(@RequestBody @Valid OAuth2CodeRequest req) {
        AccessTokenResult result = userService.exchangeOAuth2Code(req.code());
        return ResponseEntity.ok(new TokenResponse(
                result.accessToken(),
                "Bearer",
                result.accessExpiresInSec()
        ));
    }

}
