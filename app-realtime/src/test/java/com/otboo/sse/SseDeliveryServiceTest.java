package com.otboo.sse;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.*;

import com.otboo.notification.broadcast.NotificationBroadcastMessage;
import com.otboo.notification.entity.NotificationLevel;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;

import com.otboo.notification.entity.NotificationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
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
        Executor eventTaskExecutor = Runnable::run;
        sseDeliveryService = new SseDeliveryService(
            sseEmitterRepository, sseMessageRepository, eventTaskExecutor);
        ReflectionTestUtils.setField(sseDeliveryService, "timeout", 1_800_000L);
    }

    private NotificationBroadcastMessage notification(UUID receiverId) {
        return new NotificationBroadcastMessage(
            UUID.randomUUID(), Instant.now(), receiverId, null, "제목", "내용",
            NotificationLevel.INFO, NotificationType.DM_RECEIVED, null);
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

    @Nested
    @DisplayName("deliver — 전송 실패")
    class DeliverSendFailure {

        @Test
        @DisplayName("전송에 실패하면 emitter를 제거한다")
        void removesEmitterWhenSendFails() throws IOException {
            UUID receiverId = UUID.randomUUID();
            NotificationBroadcastMessage data = notification(receiverId);
            SseEmitter emitter = mock(SseEmitter.class);
            doThrow(new IOException("연결 끊김")).when(emitter).send(any(SseEmitter.SseEventBuilder.class));
            when(sseMessageRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
            when(sseEmitterRepository.findByUserId(receiverId)).thenReturn(emitter);

            sseDeliveryService.deliver(data);

            verify(sseEmitterRepository).remove(receiverId, emitter);
        }
    }

    @Nested
    @DisplayName("connect — 놓친 메시지 여러 건 replay")
    class ConnectReplaysMultipleMessages {

        @Test
        @DisplayName("놓친 메시지가 여러 건이면 모두 전송한다")
        void replaysAllMissedMessages() throws IOException {
            UUID receiverId = UUID.randomUUID();
            UUID lastEventId = UUID.randomUUID();
            SseMessage first = SseMessage.of(notification(receiverId));
            SseMessage second = SseMessage.of(notification(receiverId));
            when(sseMessageRepository.findAllAfter(lastEventId, receiverId))
                .thenReturn(List.of(first, second));

            try (MockedConstruction<SseEmitter> mocked = mockConstruction(SseEmitter.class)) {
                sseDeliveryService.connect(receiverId, lastEventId);

                SseEmitter createdEmitter = mocked.constructed().get(0);
                verify(createdEmitter, times(2)).send(any(SseEmitter.SseEventBuilder.class));
            }
        }
    }
}
