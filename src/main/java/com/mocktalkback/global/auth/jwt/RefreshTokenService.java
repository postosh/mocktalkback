package com.mocktalkback.global.auth.jwt;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RefreshTokenService {

    private static final String KEY_PREFIX = "rt:sid:";  // rt:sid:<sid> -> jti
    private static final String ABS_KEY_PREFIX = "rt:sid:abs:";  // rt:sid:abs:<sid> -> abs exp epoch sec

    private final StringRedisTemplate redis;
    private final JwtTokenProvider jwt;
    private final RedisScript<Long> rotateScript;

    public RefreshTokenService(StringRedisTemplate redis, JwtTokenProvider jwt) {
        this.redis = redis;
        this.jwt = jwt;
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("redis/refresh_rotate.lua"));
        script.setResultType(Long.class);
        this.rotateScript = script;
    }

    public IssuedRefresh issue(Long userId, boolean rememberMe) {
        String sid = UUID.randomUUID().toString();
        String jti = UUID.randomUUID().toString();

        long nowSec = Instant.now().getEpochSecond();
        long absExpEpochSec = nowSec + jwt.refreshAbsoluteTtlSec();
        long absTtlSec = absExpEpochSec - nowSec;
        if (absTtlSec <= 0) {
            throw new IllegalStateException("refresh absolute ttl must be positive");
        }
        long jtiTtlSec = Math.min(jwt.refreshTtlSec(), absTtlSec);

        saveAbsExp(sid, absExpEpochSec, absTtlSec);
        saveSidJti(sid, jti, jtiTtlSec);

        String refresh = jwt.createRefreshToken(userId, sid, jti, rememberMe);
        return new IssuedRefresh(refresh, sid, jti, jtiTtlSec);
    }

    public Rotated rotate(String refreshToken) {
        Jwt token = parseRefresh(refreshToken);

        Long userId = Long.valueOf(token.getSubject());
        String sid = token.getClaimAsString("sid");
        String jti = token.getId();
        boolean rememberMe = resolveRememberMe(token);

        if (sid == null || jti == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "REFRESH_INVALID");
        }

        String newJti = UUID.randomUUID().toString();
        long nowSec = Instant.now().getEpochSecond();

        Long result = redis.execute(
                rotateScript,
                List.of(key(sid), absKey(sid)),
                jti,
                newJti,
                String.valueOf(nowSec),
                String.valueOf(jwt.refreshTtlSec())
        );

        if (result == null || result == 0L) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "REFRESH_INVALID");
        }
        if (result < 0) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "REFRESH_EXPIRED");
        }

        String newRefresh = jwt.createRefreshToken(userId, sid, newJti, rememberMe);
        return new Rotated(userId, newRefresh, result, rememberMe);
    }

    public void revoke(String refreshToken) {
        Jwt token = parseRefresh(refreshToken);
        String sid = token.getClaimAsString("sid");
        redis.delete(List.of(key(sid), absKey(sid)));
    }

    private void saveSidJti(String sid, String jti, long ttlSec) {
        redis.opsForValue().set(key(sid), jti, Duration.ofSeconds(ttlSec));
    }

    private void saveAbsExp(String sid, long absExpEpochSec, long ttlSec) {
        redis.opsForValue().set(absKey(sid), String.valueOf(absExpEpochSec), Duration.ofSeconds(ttlSec));
    }

    private String key(String sid) {
        return KEY_PREFIX + sid;
    }

    private String absKey(String sid) {
        return ABS_KEY_PREFIX + sid;
    }

    private Jwt parseRefresh(String token) {
        try {
            return jwt.parseRefreshClaims(token);
        } catch (JwtException e) {
            if (isExpiredJwt(e)) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "REFRESH_EXPIRED");
            }
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "REFRESH_INVALID");
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "REFRESH_INVALID");
        }
    }

    private static boolean isExpiredJwt(JwtException exception) {
        if (exception instanceof JwtValidationException validation) {
            return validation.getErrors().stream()
                    .map(OAuth2Error::getDescription)
                    .anyMatch(RefreshTokenService::describesExpiry);
        }
        return describesExpiry(exception.getMessage());
    }

    private static boolean describesExpiry(String description) {
        return description != null && description.toLowerCase().contains("expired");
    }

    private boolean resolveRememberMe(Jwt jwt) {
        Object raw = jwt.getClaim("rm");
        if (raw instanceof Boolean value) {
            return value;
        }
        if (raw instanceof String value) {
            return Boolean.parseBoolean(value);
        }
        if (raw instanceof Number value) {
            return value.intValue() != 0;
        }
        return true;
    }

    public record IssuedRefresh(
            String refreshToken,
            String sid,
            String jti,
            long refreshExpiresInSec
    ) {}

    public record Rotated(
            Long userId,
            String refreshToken,
            long refreshExpiresInSec,
            boolean rememberMe
    ) {}
}