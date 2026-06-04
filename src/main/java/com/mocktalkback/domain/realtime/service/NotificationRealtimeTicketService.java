package com.mocktalkback.domain.realtime.service;

import com.mocktalkback.global.common.dto.ErrorCode;
import com.mocktalkback.global.i18n.ApiException;
import java.time.Duration;

import org.springframework.stereotype.Service;

import com.mocktalkback.domain.realtime.config.RealtimeRedisProperties;
import com.mocktalkback.domain.realtime.dto.NotificationRealtimeTicketResponse;
import com.mocktalkback.global.auth.ticket.TicketIdGenerator;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class NotificationRealtimeTicketService {

    private static final String TICKET_PREFIX = "rt_ntf_";

    private final NotificationRealtimeTicketStore notificationRealtimeTicketStore;
    private final RealtimeRedisProperties realtimeRedisProperties;
    private final TicketIdGenerator ticketIdGenerator;

    public NotificationRealtimeTicketResponse issue(Long userId) {
        if (!realtimeRedisProperties.enabled()) {
            throw new IllegalStateException("알림 SSE ticket 발급을 위한 Redis가 활성화되어 있지 않습니다.");
        }

        Duration ticketTtl = resolveTicketTtl();
        String ticket = buildTicket();
        notificationRealtimeTicketStore.save(ticket, userId, ticketTtl);

        return new NotificationRealtimeTicketResponse(ticket, ticketTtl.toSeconds());
    }

    public Long consume(String ticket) {
        if (ticket == null || ticket.isBlank()) {
            throw new ApiException(ErrorCode.NOTIFICATION_SSE_TICKET_EMPTY);
        }

        Long userId = notificationRealtimeTicketStore.consume(ticket);
        if (userId == null) {
            throw new ApiException(ErrorCode.NOTIFICATION_SSE_TICKET_INVALID);
        }
        return userId;
    }

    private Duration resolveTicketTtl() {
        Duration configuredTtl = realtimeRedisProperties.notificationTicketTtl();
        if (configuredTtl == null || configuredTtl.isNegative() || configuredTtl.isZero()) {
            return Duration.ofSeconds(30);
        }
        return configuredTtl;
    }

    private String buildTicket() {
        return ticketIdGenerator.generate(TICKET_PREFIX);
    }
}
