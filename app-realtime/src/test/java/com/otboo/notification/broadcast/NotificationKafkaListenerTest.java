package com.otboo.notification.broadcast;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.otboo.notification.entity.NotificationLevel;
import com.otboo.notification.entity.NotificationType;
import com.otboo.sse.SseDeliveryService;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NotificationKafkaListenerTest {

    private final SseDeliveryService sseDeliveryService = mock(SseDeliveryService.class);
    private final NotificationKafkaListener listener = new NotificationKafkaListener(sseDeliveryService);

    @Test
    void delegatesToSseDeliveryService() {
        NotificationBroadcastMessage message = new NotificationBroadcastMessage(
            UUID.randomUUID(), Instant.now(), UUID.randomUUID(), null,
            "제목", "내용", NotificationLevel.INFO, NotificationType.FEED_LIKED, null);

        listener.onMessage(message);

        verify(sseDeliveryService).deliver(message);
    }
}
