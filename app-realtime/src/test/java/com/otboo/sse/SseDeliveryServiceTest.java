package com.otboo.sse;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.otboo.notification.broadcast.NotificationBroadcastMessage;
import com.otboo.notification.entity.NotificationLevel;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class SseDeliveryServiceTest {

    private SseEmitterRepository sseEmitterRepository;
    private SseMessageRepository sseMessageRepository;
    private SseDeliveryService sseDeliveryService;

    @BeforeEach
    void setUp() {
        sseEmitterRepository = mock(SseEmitterRepository.class);
        sseMessageRepository = mock(SseMessageRepository.class);
        sseDeliveryService = new SseDeliveryService(sseEmitterRepository, sseMessageRepository);
        ReflectionTestUtils.setField(sseDeliveryService, "timeout", 1_800_000L);
    }

    private NotificationBroadcastMessage notification(UUID receiverId) {
        return new NotificationBroadcastMessage(
            UUID.randomUUID(), Instant.now(), receiverId, "제목", "내용", NotificationLevel.INFO);
    }

    @Nested
    @DisplayName("connect")
    class Connect {

        @Test
        @DisplayName("lastEventId가 없으면 emitter를 저장만 하고 메시지 조회는 하지 않는다")
        void withoutLastEventId() {
            UUID receiverId = UUID.randomUUID();

            SseEmitter emitter = sseDeliveryService.connect(receiverId, null);

            verify(sseEmitterRepository).save(receiverId, emitter);
            verifyNoInteractions(sseMessageRepository);
        }

        @Test
        @DisplayName("lastEventId가 있으면 놓친 메시지를 조회한다")
        void withLastEventId() {
            UUID receiverId = UUID.randomUUID();
            UUID lastEventId = UUID.randomUUID();
            SseMessage missed = SseMessage.of(notification(receiverId));
            when(sseMessageRepository.findAllAfter(lastEventId, receiverId)).thenReturn(List.of(missed));

            sseDeliveryService.connect(receiverId, lastEventId);

            verify(sseMessageRepository).findAllAfter(lastEventId, receiverId);
        }
    }

    @Nested
    @DisplayName("deliver")
    class Deliver {

        @Test
        @DisplayName("연결된 emitter가 있으면 메시지를 저장하고 조회한다")
        void success() {
            UUID receiverId = UUID.randomUUID();
            NotificationBroadcastMessage data = notification(receiverId);
            SseEmitter emitter = new SseEmitter();
            when(sseMessageRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
            when(sseEmitterRepository.findByUserId(receiverId)).thenReturn(emitter);

            sseDeliveryService.deliver(data);

            verify(sseMessageRepository).save(any(SseMessage.class));
            verify(sseEmitterRepository).findByUserId(receiverId);
        }

        @Test
        @DisplayName("연결된 emitter가 없으면 저장만 하고 전송은 시도하지 않는다")
        void noConnectedEmitter() {
            UUID receiverId = UUID.randomUUID();
            NotificationBroadcastMessage data = notification(receiverId);
            when(sseMessageRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
            when(sseEmitterRepository.findByUserId(receiverId)).thenReturn(null);

            sseDeliveryService.deliver(data);

            verify(sseMessageRepository).save(any(SseMessage.class));
            verify(sseEmitterRepository).findByUserId(receiverId);
            verifyNoMoreInteractions(sseEmitterRepository);
        }
    }

    @Nested
    @DisplayName("sendHeartbeat")
    class SendHeartbeat {

        @Test
        @DisplayName("ping에 실패한 emitter는 완료 처리하고 성공한 emitter는 유지한다")
        void completesDeadEmitters() throws IOException {
            SseEmitter healthy = mock(SseEmitter.class);
            SseEmitter dead = mock(SseEmitter.class);
            doThrow(new IOException("연결 끊김")).when(dead).send(anySet());
            when(sseEmitterRepository.findAll()).thenReturn(List.of(healthy, dead));

            sseDeliveryService.sendHeartbeat();

            verify(dead).complete();
            verify(healthy, never()).complete();
        }
    }
}
