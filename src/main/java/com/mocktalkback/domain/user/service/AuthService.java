package com.mocktalkback.domain.user.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.mocktalkback.domain.role.entity.RoleEntity;
import com.mocktalkback.domain.role.repository.RoleRepository;
import com.mocktalkback.domain.role.type.RoleNames;
import com.mocktalkback.domain.user.dto.AuthTokens;
import com.mocktalkback.domain.user.dto.AccessTokenResult;
import com.mocktalkback.domain.user.dto.JoinRequest;
import com.mocktalkback.domain.user.dto.LoginRequest;
import com.mocktalkback.domain.user.dto.RefreshTokens;
import com.mocktalkback.domain.user.entity.UserEntity;
import com.mocktalkback.domain.user.repository.UserRepository;
import com.mocktalkback.global.auth.jwt.JwtTokenProvider;
import com.mocktalkback.global.auth.jwt.RefreshTokenService;
import com.mocktalkback.global.auth.jwt.RefreshTokenService.Rotated;
import com.mocktalkback.global.auth.oauth2.OAuth2CodeService;
import com.mocktalkback.global.common.dto.ErrorCode;
import com.mocktalkback.global.common.util.HandleGenerator;
import com.mocktalkback.global.common.util.ActivityPointPolicy;
import com.mocktalkback.global.i18n.ApiException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {
    
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwt;
    private final RefreshTokenService refreshTokenService;
    private final OAuth2CodeService oAuth2CodeService;
    private final HandleGenerator handleGenerator;
    
    @Transactional
    public void join(JoinRequest joinDto) {
        if (!joinDto.password().equals(joinDto.confirmPassword())) {
            throw new ApiException(ErrorCode.USER_PASSWORD_MISMATCH);
        }

        String loginId = joinDto.loginId().trim();
        if (!StringUtils.hasText(loginId)) {
            throw new ApiException(ErrorCode.USER_LOGIN_ID_REQUIRED);
        }
        if (userRepository.existsByLoginId(loginId)) {
            throw new ApiException(ErrorCode.USER_LOGIN_ID_ALREADY_EXISTS);
        }
        String email = joinDto.email().trim();
        if (userRepository.existsByEmail(email)) {
            throw new ApiException(ErrorCode.USER_EMAIL_ALREADY_EXISTS);
        }

        String userName = resolveOptional(joinDto.userName(), loginId);
        String displayName = resolveOptional(joinDto.displayName(), userName);

        requireMaxLength(userName, 32, "사용자명");
        requireMaxLength(displayName, 16, "표시명");

        String handle;
        if (StringUtils.hasText(joinDto.handle())) {
            handle = joinDto.handle().trim();
            requireMaxLength(handle, 24, "핸들");
            if (userRepository.existsByHandle(handle)) {
                throw new ApiException(ErrorCode.USER_HANDLE_ALREADY_EXISTS);
            }
        } else {
            handle = handleGenerator.generateUniqueHandle();
        }

        RoleEntity role = roleRepository.findByRoleName(RoleNames.WRITER)
                .orElseThrow(() -> new IllegalStateException("기본 권한이 없습니다."));

        String encodedPw = passwordEncoder.encode(joinDto.password());
        UserEntity user = UserEntity.createLocal(
                role,
                loginId,
                email,
                encodedPw,
                userName,
                displayName,
                handle
        );
        user.changePoint(ActivityPointPolicy.JOIN.delta);

        userRepository.save(user);
    }

    private String resolveOptional(String value, String fallback) {
        if (StringUtils.hasText(value)) {
            return value.trim();
        }
        return fallback;
    }

    private void requireMaxLength(String value, int max, String fieldName) {
        if (value.length() > max) {
            throw new ApiException(ErrorCode.USER_FIELD_MAX_LENGTH, fieldName, max);
        }
    }

    @Transactional(readOnly = true)
    public AuthTokens login(LoginRequest req) {
        UserEntity u = userRepository.findByLoginId(req.loginId())
                .orElseThrow(() -> new ApiException(ErrorCode.AUTH_INVALID_CREDENTIALS));

        if (u.isDeleted()) {
            throw new ApiException(ErrorCode.AUTH_ACCOUNT_WITHDRAWN);
        }
        if (!u.isEnabled() || u.isLocked()) {
            throw new ApiException(ErrorCode.AUTH_ACCOUNT_DISABLED);
        }

        if (!passwordEncoder.matches(req.password(), u.getPwHash())) {
            throw new ApiException(ErrorCode.AUTH_INVALID_CREDENTIALS);
        }

        String token = jwt.createAccessToken(
                u.getId(),
                u.getRole().getRoleName(),
                u.getRole().getAuthBit()
        );

        RefreshTokenService.IssuedRefresh issued = refreshTokenService.issue(u.getId(), req.rememberMe());

        return new AuthTokens(token, jwt.accessTtlSec(), issued.refreshToken(), issued.refreshExpiresInSec());
    }

    @Transactional(readOnly = true)
    public RefreshTokens refresh(String refreshToken) {
        Rotated rotated = refreshTokenService.rotate(refreshToken);

        UserEntity user = userRepository.findById(rotated.userId())
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));

        if (!user.isEnabled() || user.isLocked()) {
            try {
                refreshTokenService.revoke(refreshToken);
            } catch (ApiException ignored) {
            }
            throw new ApiException(ErrorCode.COMMON_UNAUTHORIZED);
        }

        String access = jwt.createAccessToken(
                user.getId(),
                user.getRole().getRoleName(),
                user.getRole().getAuthBit()
        );

        return new RefreshTokens(
                access,
                jwt.accessTtlSec(),
                rotated.refreshToken(),
                rotated.refreshExpiresInSec(),
                rotated.rememberMe()
        );
    }

    @Transactional(readOnly = true)
    public AccessTokenResult exchangeOAuth2Code(String code) {
        Long userId = oAuth2CodeService.consume(code);
        if (userId == null) {
            throw new ApiException(ErrorCode.OAUTH2_CODE_INVALID);
        }

        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));

        if (!user.isEnabled() || user.isLocked()) {
            throw new ApiException(ErrorCode.COMMON_UNAUTHORIZED);
        }

        String access = jwt.createAccessToken(
                user.getId(),
                user.getRole().getRoleName(),
                user.getRole().getAuthBit()
        );

        return new AccessTokenResult(access, jwt.accessTtlSec());
    }

}