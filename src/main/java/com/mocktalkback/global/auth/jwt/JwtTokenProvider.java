package com.mocktalkback.global.auth.jwt;

import java.time.Instant;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenProvider {

    private final JwtEncoder jwtEncoder;
    private final JwtDecoder jwtDecoder;
    private final String issuer;
    private final long accessTtlSec;
    private final long refreshTtlSec;
    private final long refreshAbsoluteTtlSec;

    public JwtTokenProvider(
            JwtEncoder jwtEncoder,
            @Qualifier("mocktalkJwtDecoder") JwtDecoder jwtDecoder,
            @Value("${JWT_ISSUER:mocktalk}") String issuer,
            @Value("${JWT_ACCESS_TTL_SECONDS:3600}") long accessTtlSec,
            @Value("${JWT_REFRESH_TTL_SECONDS:1209600}") long refreshTtlSec,
            @Value("${JWT_REFRESH_ABSOLUTE_TTL_SECONDS:2592000}") long refreshAbsoluteTtlSec
    ) {
        this.jwtEncoder = jwtEncoder;
        this.jwtDecoder = jwtDecoder;
        this.issuer = issuer;
        this.accessTtlSec = accessTtlSec;
        this.refreshTtlSec = refreshTtlSec;
        this.refreshAbsoluteTtlSec = refreshAbsoluteTtlSec;
    }

    public long accessTtlSec() {
        return accessTtlSec;
    }

    public long refreshTtlSec() {
        return refreshTtlSec;
    }

    public long refreshAbsoluteTtlSec() {
        return refreshAbsoluteTtlSec;
    }

    public String createAccessToken(Long userId, String roleName, int authBit) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(String.valueOf(userId))
                .issuedAt(now)
                .expiresAt(now.plusSeconds(accessTtlSec))
                .claim("typ", "access")
                .claim("role", roleName)
                .claim("authBit", authBit)
                .build();
        return encode(claims);
    }

    public String createRefreshToken(Long userId, String sid, String jti, boolean rememberMe) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(String.valueOf(userId))
                .issuedAt(now)
                .expiresAt(now.plusSeconds(refreshTtlSec))
                .id(jti)
                .claim("typ", "refresh")
                .claim("sid", sid)
                .claim("rm", rememberMe)
                .build();
        return encode(claims);
    }

    public Jwt decode(String token) {
        return jwtDecoder.decode(token);
    }

    public Jwt parseRefreshClaims(String token) throws JwtException {
        Jwt jwt = jwtDecoder.decode(token);
        if (!"refresh".equals(jwt.getClaimAsString("typ"))) {
            throw new JwtException("not refresh token");
        }
        return jwt;
    }

    private String encode(JwtClaimsSet claims) {
        JwsHeader headers = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(headers, claims)).getTokenValue();
    }
}