package com.otboo.sse;

import static org.assertj.core.api.Assertions.assertThat;

import com.otboo.notification.broadcast.NotificationBroadcastMessage;
import com.otboo.notification.entity.NotificationLevel;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.otboo.notification.entity.NotificationType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class SseMessageRepositoryTest {

    private final SseMessageRepository sseMessageRepository = new SseMessageRepository(50);

    private SseMessage message(UUID receiverId) {
        NotificationBroadcastMessage data = new NotificationBroadcastMessage(
            UUID.randomUUID(), Instant.now(), receiverId, null, "제목", "내용",
            NotificationLevel.INFO, NotificationType.DM_RECEIVED, null);
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
        @DisplayName("한 유저가 capacity를 초과해도 다른 유저의 메시지는 밀려나지 않는다")
        void doesNotEvictOtherReceiverWhenOneReceiverExceedsCapacity() {
            SseMessageRepository repository = new SseMessageRepository(2);
            UUID busyReceiverId = UUID.randomUUID();
            UUID quietReceiverId = UUID.randomUUID();

            SseMessage quietFirst = message(quietReceiverId);
            SseMessage quietSecond = message(quietReceiverId);
            repository.save(quietFirst);
            repository.save(quietSecond);

            for (int i = 0; i < 5; i++) {
                repository.save(message(busyReceiverId));
            }

            assertThat(repository.findAllAfter(quietFirst.eventId(), quietReceiverId))
                .containsExactly(quietSecond);
        }
    }

    @Nested
    @DisplayName("evictStaleBuffers")
    class EvictStaleBuffers {

        @Test
        @DisplayName("TTL이 지난 유저의 버퍼는 삭제되어, 이후 조회하면 빈 리스트가 반환된다")
        void evictsStaleBuffer() {
            UUID receiverId = UUID.randomUUID();
            SseMessage first = message(receiverId);
            SseMessage second = message(receiverId);
            sseMessageRepository.save(first);
            sseMessageRepository.save(second);

            // 정리 전: 정상 조회됨
            assertThat(sseMessageRepository.findAllAfter(first.eventId(), receiverId))
                .containsExactly(second);

            // lastSavedAt을 TTL(1시간)보다 더 과거로 강제 조작
            Object buffer = ((Map<?, ?>) ReflectionTestUtils.getField(sseMessageRepository, "buffers"))
                .get(receiverId);
            ReflectionTestUtils.setField(buffer, "lastSavedAt", Instant.now().minus(Duration.ofHours(1).plusMinutes(1)));

            sseMessageRepository.evictStaleBuffers();

            // 정리 후: 버퍼 자체가 사라져서 어떤 eventId를 넣어도 빈 리스트
            assertThat(sseMessageRepository.findAllAfter(first.eventId(), receiverId)).isEmpty();
        }

        @Test
        @DisplayName("TTL이 지나지 않은 유저의 버퍼는 정리 대상에서 제외된다")
        void doesNotEvictFreshBuffer() {
            UUID receiverId = UUID.randomUUID();
            SseMessage first = message(receiverId);
            SseMessage second = message(receiverId);
            sseMessageRepository.save(first);
            sseMessageRepository.save(second);

            sseMessageRepository.evictStaleBuffers();

            assertThat(sseMessageRepository.findAllAfter(first.eventId(), receiverId))
                .containsExactly(second);
        }
    }
}
