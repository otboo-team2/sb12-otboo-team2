package com.otboo.dm.broadcast;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisEventBroadcaster implements com.otboo.common.broadcast.EventBroadcaster {

    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public void broadcast(String channel, Object message) {
        redisTemplate.convertAndSend(channel, message);
    }
}
