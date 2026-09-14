package com.otboo.dm.broadcast;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DirectMessageNotificationSubscriber implements MessageListener {

    private final RedisTemplate<String, Object> redisTemplate;
    private final DirectMessageEventPublisher directMessageEventPublisher;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        Object value = redisTemplate.getValueSerializer().deserialize(message.getBody());
        // log.info("[DM-NOTIFY] Redis 메시지 수신, value={}", value);

        if (value instanceof DirectMessageBroadcastMessage broadcastMessage) {
            directMessageEventPublisher.publish(broadcastMessage);
            // log.info("[DM-NOTIFY] DirectMessageReceivedEvent 발행, id={}", broadcastMessage.id());
        }
    }
}
