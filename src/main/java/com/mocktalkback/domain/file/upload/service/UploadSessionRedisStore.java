package com.mocktalkback.domain.file.upload.service;

import com.mocktalkback.global.common.dto.ErrorCode;
import com.mocktalkback.global.i18n.ApiException;
import java.time.Duration;
import java.util.Optional;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Component
public class UploadSessionRedisStore {

    private static final String KEY_PREFIX = "upload:session:";

    private final StringRedisTemplate stringRedisTemplate;
    private final JsonMapper objectMapper;

    public UploadSessionRedisStore(
        StringRedisTemplate stringRedisTemplate,
        JsonMapper objectMapper
    ) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
    }

    public void save(UploadSessionState state, Duration ttl) {
        if (state == null) {
            throw new ApiException(ErrorCode.UPLOAD_SESSION_STATE_EMPTY);
        }
        String serialized = serialize(state);
        stringRedisTemplate.opsForValue().set(key(state.uploadToken()), serialized, ttl);
    }

    public Optional<UploadSessionState> consume(String uploadToken) {
        String raw = stringRedisTemplate.opsForValue().getAndDelete(key(uploadToken));
        if (!StringUtils.hasText(raw)) {
            return Optional.empty();
        }
        return Optional.of(deserialize(raw));
    }

    public Optional<UploadSessionState> find(String uploadToken) {
        String raw = stringRedisTemplate.opsForValue().get(key(uploadToken));
        if (!StringUtils.hasText(raw)) {
            return Optional.empty();
        }
        return Optional.of(deserialize(raw));
    }

    public void delete(String uploadToken) {
        stringRedisTemplate.delete(key(uploadToken));
    }

    private String key(String uploadToken) {
        if (!StringUtils.hasText(uploadToken)) {
            throw new ApiException(ErrorCode.UPLOAD_TOKEN_EMPTY);
        }
        return KEY_PREFIX + uploadToken.trim();
    }

    private String serialize(UploadSessionState state) {
        try {
            return objectMapper.writeValueAsString(state);
        } catch (JacksonException ex) {
            throw new IllegalStateException("업로드 세션 저장 직렬화에 실패했습니다.");
        }
    }

    private UploadSessionState deserialize(String raw) {
        try {
            return objectMapper.readValue(raw, UploadSessionState.class);
        } catch (JacksonException ex) {
            throw new IllegalStateException("업로드 세션 역직렬화에 실패했습니다.");
        }
    }
}
