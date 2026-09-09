package com.otboo.notification.broadcast;

import com.otboo.sse.SseDeliveryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationRedisSubscriber implements MessageListener {

    private final RedisTemplate<String, Object> redisTemplate;
    private final SseDeliveryService sseDeliveryService;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        Object value = redisTemplate.getValueSerializer().deserialize(message.getBody());
        // log.info("[REDIS-SUBSCRIBE] 메시지 수신, value={}", value);

        if (value instanceof NotificationBroadcastMessage broadcastMessage) {
            // log.info("[REDIS-SUBSCRIBE] receiverId={}", broadcastMessage.receiverId());
            sseDeliveryService.deliver(broadcastMessage);
        } else {
            // log.warn("[REDIS-SUBSCRIBE] 알 수 없는 메시지 타입 수신: {}", value);
        }
    }
}
