package com.otboo.dm.broadcast;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class DirectMessageNotificationSubscriber {

    private final DirectMessageEventPublisher directMessageEventPublisher;

    @KafkaListener(topics = "dm-broadcast", groupId = "app-api-dm-consumer")
    public void onMessage(DirectMessageBroadcastMessage message) {
        directMessageEventPublisher.publish(message);
    }
}
