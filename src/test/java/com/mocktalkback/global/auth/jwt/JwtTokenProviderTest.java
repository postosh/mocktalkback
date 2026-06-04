package com.mocktalkback.global.auth.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(classes = {JwtSecurityConfig.class, JwtTokenProvider.class})
@TestPropertySource(properties = {
        "JWT_SECRET=abcdefghijklmnopqrstuvwxyz012345",
        "JWT_ISSUER=mocktalk-test"
})
class JwtTokenProviderTest {

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    @Qualifier("accessJwtDecoder")
    private JwtDecoder accessJwtDecoder;

    @Test
    void access_token_round_trips_through_access_decoder() {
        String token = jwtTokenProvider.createAccessToken(7L, "ADMIN", 3);

        Jwt jwt = accessJwtDecoder.decode(token);

        assertThat(jwt.getSubject()).isEqualTo("7");
        assertThat(jwt.getClaimAsString("typ")).isEqualTo("access");
        assertThat(jwt.getClaimAsString("role")).isEqualTo("ADMIN");
        assertThat(((Number) jwt.getClaim("authBit")).intValue()).isEqualTo(3);
    }

    @Test
    void refresh_token_rejects_access_decoder() {
        String refresh = jwtTokenProvider.createRefreshToken(1L, "sid-1", "jti-1", true);

        assertThatThrownBy(() -> accessJwtDecoder.decode(refresh))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void refresh_token_parses_via_provider() {
        String refresh = jwtTokenProvider.createRefreshToken(9L, "sid-9", "jti-9", false);

        Jwt jwt = jwtTokenProvider.parseRefreshClaims(refresh);

        assertThat(jwt.getSubject()).isEqualTo("9");
        assertThat(jwt.getClaimAsString("typ")).isEqualTo("refresh");
        assertThat(jwt.getClaimAsString("sid")).isEqualTo("sid-9");
        assertThat(jwt.getId()).isEqualTo("jti-9");
        assertThat(jwt.getClaimAsBoolean("rm")).isFalse();
    }
}