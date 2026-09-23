package com.otboo.sse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class SseEmitterRepositoryTest {

    private final SseEmitterRepository sseEmitterRepository = new SseEmitterRepository();

    @Nested
    @DisplayName("save")
    class Save {

        @Test
        @DisplayName("저장한 emitter는 findByUserId로 조회된다")
        void success() {
            UUID userId = UUID.randomUUID();
            SseEmitter emitter = new SseEmitter();

            sseEmitterRepository.save(userId, emitter);

            assertThat(sseEmitterRepository.findByUserId(userId)).isSameAs(emitter);
        }

        @Test
        @DisplayName("같은 유저의 기존 emitter가 있으면 새 emitter로 교체하고 기존 emitter를 완료시킨다")
        void replacesPreviousEmitter() {
            UUID userId = UUID.randomUUID();
            SseEmitter previous = mock(SseEmitter.class);
            SseEmitter next = mock(SseEmitter.class);

            sseEmitterRepository.save(userId, previous);
            sseEmitterRepository.save(userId, next);

            assertThat(sseEmitterRepository.findByUserId(userId)).isSameAs(next);
            verify(previous).complete();
        }
    }

    @Nested
    @DisplayName("remove")
    class Remove {

        @Test
        @DisplayName("현재 매핑된 emitter와 같으면 삭제한다")
        void success() {
            UUID userId = UUID.randomUUID();
            SseEmitter emitter = new SseEmitter();
            sseEmitterRepository.save(userId, emitter);

            sseEmitterRepository.remove(userId, emitter);

            assertThat(sseEmitterRepository.findByUserId(userId)).isNull();
        }

        @Test
        @DisplayName("현재 매핑된 emitter와 다르면 삭제하지 않는다")
        void ignoresStaleEmitter() {
            UUID userId = UUID.randomUUID();
            SseEmitter previous = new SseEmitter();
            SseEmitter next = new SseEmitter();
            sseEmitterRepository.save(userId, previous);
            sseEmitterRepository.save(userId, next);

            sseEmitterRepository.remove(userId, previous);

            assertThat(sseEmitterRepository.findByUserId(userId)).isSameAs(next);
        }
    }

    @Nested
    @DisplayName("findAll")
    class FindAll {

        @Test
        @DisplayName("모든 유저의 emitter를 반환한다")
        void success() {
            SseEmitter emitter1 = new SseEmitter();
            SseEmitter emitter2 = new SseEmitter();
            sseEmitterRepository.save(UUID.randomUUID(), emitter1);
            sseEmitterRepository.save(UUID.randomUUID(), emitter2);

            assertThat(sseEmitterRepository.findAll()).containsExactlyInAnyOrder(emitter1, emitter2);
        }
    }
}
