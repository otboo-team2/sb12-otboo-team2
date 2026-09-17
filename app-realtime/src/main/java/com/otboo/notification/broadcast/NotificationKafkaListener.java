package com.otboo.notification.broadcast;

import com.otboo.sse.SseDeliveryService;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NotificationKafkaListener {

    private final SseDeliveryService sseDeliveryService;

    @KafkaListener(topics = "notification-broadcast", groupId = "#{@realtimeConsumerGroupId}")
    public void onMessage(NotificationBroadcastMessage message) {
        sseDeliveryService.deliver(message);
    }
}
