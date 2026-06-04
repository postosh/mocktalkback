package com.mocktalkback.domain.realtime.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import com.mocktalkback.domain.realtime.config.RealtimeRedisProperties;
import com.mocktalkback.domain.realtime.dto.NotificationPresenceUpdateRequest;
import com.mocktalkback.domain.realtime.type.NotificationPresenceViewType;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class NotificationPresenceRedisStore {

    private static final String PRESENCE_KEY_PREFIX = "presence:notification:";

    private final StringRedisTemplate stringRedisTemplate;
    private final JsonMapper objectMapper;
    private final RealtimeRedisProperties realtimeRedisProperties;

    public void upsert(Long userId, NotificationPresenceUpdateRequest request, Instant now) {
        RedisPresenceState state = new RedisPresenceState(
            request.sessionId(),
            request.viewType(),
            request.articleId(),
            request.notificationPanelOpen(),
            now
        );
        String key = key(userId, request.sessionId());
        String value = toJson(state);
        stringRedisTemplate.opsForValue().set(key, value, realtimeRedisProperties.presenceTtl());
        evictOverflow(userId, realtimeRedisProperties.presenceMaxSessions());
    }

    public void remove(Long userId, String sessionId) {
        stringRedisTemplate.delete(key(userId, sessionId));
    }

    public List<RedisPresenceState> findByUserId(Long userId) {
        Set<String> keys = stringRedisTemplate.keys(prefix(userId) + "*");
        if (keys == null || keys.isEmpty()) {
            return List.of();
        }
        List<String> values = stringRedisTemplate.opsForValue().multiGet(keys);
        if (values == null || values.isEmpty()) {
            return List.of();
        }

        List<RedisPresenceState> states = new ArrayList<>();
        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            states.add(fromJson(value));
        }
        return states;
    }

    private void evictOverflow(Long userId, int maxSessions) {
        List<RedisPresenceState> states = new ArrayList<>(findByUserId(userId));
        if (states.size() < maxSessions) {
            return;
        }

        states.sort(Comparator.comparing(RedisPresenceState::lastSeenAt));
        int removeCount = states.size() - maxSessions + 1;
        for (int i = 0; i < removeCount; i++) {
            RedisPresenceState state = states.get(i);
            stringRedisTemplate.delete(key(userId, state.sessionId()));
        }
    }

    private String key(Long userId, String sessionId) {
        return prefix(userId) + sessionId;
    }

    private String prefix(Long userId) {
        return PRESENCE_KEY_PREFIX + userId + ":";
    }

    private String toJson(RedisPresenceState state) {
        try {
            return objectMapper.writeValueAsString(state);
        } catch (JacksonException ex) {
            throw new IllegalStateException("알림 presence 직렬화에 실패했습니다.", ex);
        }
    }

    private RedisPresenceState fromJson(String value) {
        try {
            return objectMapper.readValue(value, RedisPresenceState.class);
        } catch (JacksonException ex) {
            throw new IllegalStateException("알림 presence 파싱에 실패했습니다.", ex);
        }
    }

    public record RedisPresenceState(
        String sessionId,
        NotificationPresenceViewType viewType,
        Long articleId,
        boolean notificationPanelOpen,
        Instant lastSeenAt
    ) {
    }
}
