package com.mocktalkback.global.auth.jwt;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.StringUtils;

public class MocktalkAccessTokenValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error NOT_ACCESS_TOKEN =
            new OAuth2Error("invalid_token", "not access token", null);
    private static final OAuth2Error MISSING_ROLE =
            new OAuth2Error("invalid_token", "missing role", null);

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        if (!"access".equals(jwt.getClaimAsString("typ"))) {
            return OAuth2TokenValidatorResult.failure(NOT_ACCESS_TOKEN);
        }
        String role = jwt.getClaimAsString("role");
        if (!StringUtils.hasText(role)) {
            return OAuth2TokenValidatorResult.failure(MISSING_ROLE);
        }
        return OAuth2TokenValidatorResult.success();
    }
}