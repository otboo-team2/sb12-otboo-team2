package com.otboo.sse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.otboo.common.security.AuthPrincipal;
import com.otboo.user.entity.Role;
import java.io.IOException;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class SseControllerTest {

    private final SseDeliveryService sseDeliveryService = mock(SseDeliveryService.class);
    private final SseController controller = new SseController(sseDeliveryService);

    @Nested
    @DisplayName("connect")
    class Connect {

        @Test
        @DisplayName("쿼리파라미터 LastEventId가 있으면 헤더보다 우선 사용한다")
        void prefersQueryParamOverHeader() {
            AuthPrincipal me = new AuthPrincipal(UUID.randomUUID(), "me@test.com", Role.USER);
            UUID fromParam = UUID.randomUUID();
            UUID fromHeader = UUID.randomUUID();
            SseEmitter emitter = new SseEmitter();
            when(sseDeliveryService.connect(me.userId(), fromParam)).thenReturn(emitter);

            SseEmitter result = controller.connect(me, fromParam, fromHeader);

            verify(sseDeliveryService).connect(me.userId(), fromParam);
            assertThat(result).isSameAs(emitter);
        }

        @Test
        @DisplayName("쿼리파라미터가 없으면 헤더의 Last-Event-ID를 사용한다")
        void fallsBackToHeaderWhenParamMissing() {
            AuthPrincipal me = new AuthPrincipal(UUID.randomUUID(), "me@test.com", Role.USER);
            UUID fromHeader = UUID.randomUUID();
            when(sseDeliveryService.connect(me.userId(), fromHeader)).thenReturn(new SseEmitter());

            controller.connect(me, null, fromHeader);

            verify(sseDeliveryService).connect(me.userId(), fromHeader);
        }

        @Test
        @DisplayName("둘 다 없으면 null을 전달한다")
        void passesNullWhenNeitherPresent() {
            AuthPrincipal me = new AuthPrincipal(UUID.randomUUID(), "me@test.com", Role.USER);
            when(sseDeliveryService.connect(me.userId(), null)).thenReturn(new SseEmitter());

            controller.connect(me, null, null);

            verify(sseDeliveryService).connect(me.userId(), null);
        }
    }

    @Nested
    @DisplayName("handleSseClientDisconnected")
    class HandleSseClientDisconnected {

        @Test
        @DisplayName("예외를 삼키고 아무 것도 하지 않는다")
        void swallowsExceptionSilently() {
            assertThatCode(() -> controller.handleSseClientDisconnected(new IOException("연결 끊김")))
                .doesNotThrowAnyException();
            verifyNoInteractions(sseDeliveryService);
        }
    }
}
