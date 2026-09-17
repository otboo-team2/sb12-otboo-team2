package com.otboo.notification.broadcast;

import com.otboo.common.broadcast.EventBroadcaster;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class KafkaEventBroadcaster implements EventBroadcaster {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Override
    public void broadcast(String channel, Object message) {
        kafkaTemplate.send(channel, message);
    }
}
