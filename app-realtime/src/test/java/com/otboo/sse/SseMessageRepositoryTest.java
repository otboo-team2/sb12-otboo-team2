package com.otboo.sse;

import static org.assertj.core.api.Assertions.assertThat;

import com.otboo.notification.broadcast.NotificationBroadcastMessage;
import com.otboo.notification.entity.NotificationLevel;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class SseMessageRepositoryTest {

    private final SseMessageRepository sseMessageRepository = new SseMessageRepository();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(sseMessageRepository, "eventQueueCapacity", 1000);
    }

    private SseMessage message(UUID receiverId) {
        NotificationBroadcastMessage data = new NotificationBroadcastMessage(
            UUID.randomUUID(), Instant.now(), receiverId, "제목", "내용", NotificationLevel.INFO);
        return SseMessage.of(data);
    }

    @Nested
    @DisplayName("findAllAfter")
    class FindAllAfter {

        @Test
        @DisplayName("lastEventId 이후에 저장된 같은 수신자 메시지만 순서대로 반환한다")
        void success() {
            UUID receiverId = UUID.randomUUID();
            SseMessage first = message(receiverId);
            SseMessage second = message(receiverId);
            SseMessage third = message(receiverId);

            sseMessageRepository.save(first);
            sseMessageRepository.save(second);
            sseMessageRepository.save(third);

            List<SseMessage> result = sseMessageRepository.findAllAfter(first.eventId(), receiverId);

            assertThat(result).containsExactly(second, third);
        }

        @Test
        @DisplayName("다른 수신자의 메시지는 제외한다")
        void excludesOtherReceiver() {
            UUID receiverId = UUID.randomUUID();
            UUID otherReceiverId = UUID.randomUUID();
            SseMessage base = message(receiverId);
            SseMessage forOther = message(otherReceiverId);
            SseMessage forMe = message(receiverId);

            sseMessageRepository.save(base);
            sseMessageRepository.save(forOther);
            sseMessageRepository.save(forMe);

            List<SseMessage> result = sseMessageRepository.findAllAfter(base.eventId(), receiverId);

            assertThat(result).containsExactly(forMe);
        }

        @Test
        @DisplayName("lastEventId가 큐에 존재하지 않으면 빈 리스트를 반환한다")
        void notFound() {
            UUID receiverId = UUID.randomUUID();
            sseMessageRepository.save(message(receiverId));

            List<SseMessage> result = sseMessageRepository.findAllAfter(UUID.randomUUID(), receiverId);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("save")
    class Save {

        @Test
        @DisplayName("capacity를 초과하면 가장 오래된 메시지부터 제거한다")
        void evictsOldestWhenCapacityExceeded() {
            ReflectionTestUtils.setField(sseMessageRepository, "eventQueueCapacity", 2);
            UUID receiverId = UUID.randomUUID();
            SseMessage first = message(receiverId);
            SseMessage second = message(receiverId);
            SseMessage third = message(receiverId);

            sseMessageRepository.save(first);
            sseMessageRepository.save(second);
            sseMessageRepository.save(third);

            assertThat(sseMessageRepository.findAllAfter(second.eventId(), receiverId)).containsExactly(third);
            assertThat(sseMessageRepository.findAllAfter(first.eventId(), receiverId)).isEmpty();
        }
    }
}
