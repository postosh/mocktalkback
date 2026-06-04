package com.mocktalkback.domain.realtime.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import com.mocktalkback.domain.realtime.config.RealtimeRedisProperties;
import com.mocktalkback.domain.realtime.dto.NotificationRealtimeTicketResponse;
import com.mocktalkback.global.auth.ticket.TicketIdGenerator;
import com.mocktalkback.global.common.dto.ErrorCode;
import com.mocktalkback.global.i18n.ApiException;

class NotificationRealtimeTicketServiceTest {

    // ticket 발급 시 Redis 저장소에 TTL과 함께 저장하고 응답에 만료 시간을 담아야 한다.
    @Test
    void issue_saves_ticket_with_ttl() {
        // Given: ticket 저장소와 서비스
        NotificationRealtimeTicketStore ticketStore = mock(NotificationRealtimeTicketStore.class);
        RealtimeRedisProperties properties = new RealtimeRedisProperties(
            true,
            true,
            "notification",
            "board",
            Duration.ofSeconds(30),
            Duration.ofSeconds(45),
            8
        );
        TicketIdGenerator ticketIdGenerator = mock(TicketIdGenerator.class);
        when(ticketIdGenerator.generate("rt_ntf_")).thenReturn("rt_ntf_test_ticket");
        NotificationRealtimeTicketService service =
            new NotificationRealtimeTicketService(ticketStore, properties, ticketIdGenerator);

        // When: ticket 발급
        NotificationRealtimeTicketResponse response = service.issue(15L);

        // Then: 저장소에 TTL과 함께 저장되고 응답 값도 채워짐
        assertThat(response.ticket()).isEqualTo("rt_ntf_test_ticket");
        assertThat(response.expiresInSec()).isEqualTo(30L);
        verify(ticketStore).save(eq(response.ticket()), eq(15L), eq(Duration.ofSeconds(30)));
    }

    // 유효한 ticket 소비 시 사용자 식별자를 반환해야 한다.
    @Test
    void consume_returns_user_id_when_ticket_is_valid() {
        // Given: ticket 저장소와 서비스
        NotificationRealtimeTicketStore ticketStore = mock(NotificationRealtimeTicketStore.class);
        when(ticketStore.consume("valid-ticket")).thenReturn(101L);
        TicketIdGenerator ticketIdGenerator = mock(TicketIdGenerator.class);
        NotificationRealtimeTicketService service =
            new NotificationRealtimeTicketService(ticketStore, RealtimeRedisProperties.defaults(), ticketIdGenerator);

        // When: ticket 소비
        Long userId = service.consume("valid-ticket");

        // Then: ticket 소유 사용자 식별자가 반환됨
        assertThat(userId).isEqualTo(101L);
    }

    // 만료되었거나 없는 ticket 소비 시 예외를 발생시켜야 한다.
    @Test
    void consume_throws_when_ticket_is_invalid() {
        // Given: ticket 저장소와 서비스
        NotificationRealtimeTicketStore ticketStore = mock(NotificationRealtimeTicketStore.class);
        when(ticketStore.consume(any())).thenReturn(null);
        TicketIdGenerator ticketIdGenerator = mock(TicketIdGenerator.class);
        NotificationRealtimeTicketService service =
            new NotificationRealtimeTicketService(ticketStore, RealtimeRedisProperties.defaults(), ticketIdGenerator);

        // When & Then: 잘못된 ticket 소비는 ApiException이 발생함
        assertThatThrownBy(() -> service.consume("missing-ticket"))
            .isInstanceOf(ApiException.class)
            .extracting(ex -> ((ApiException) ex).getErrorCode())
            .isEqualTo(ErrorCode.NOTIFICATION_SSE_TICKET_INVALID);
    }
}
