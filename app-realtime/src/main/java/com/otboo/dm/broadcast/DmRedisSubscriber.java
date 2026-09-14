package com.otboo.dm.broadcast;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DmRedisSubscriber implements MessageListener {

    private static final String DESTINATION_PREFIX = "/sub/direct-messages_";

    private final RedisTemplate<String, Object> redisTemplate;
    private final SimpMessagingTemplate messagingTemplate;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        Object value = redisTemplate.getValueSerializer().deserialize(message.getBody());
        // log.info("[DM-SUBSCRIBE] Redis 메시지 수신, value={}", value);

        if (value instanceof DirectMessageBroadcastMessage broadcastMessage) {
            String destination = DESTINATION_PREFIX + broadcastMessage.dmKey();
            messagingTemplate.convertAndSend(destination, broadcastMessage);
            // log.info("[DM-SUBSCRIBE] STOMP 전달 완료, destination={}", destination);
        } else {
            // log.warn("[DM-SUBSCRIBE] 알 수 없는 메시지 타입 수신: {}", value);
        }
    }
}
