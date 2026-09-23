package com.otboo.dm.broadcast;

import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DmKafkaListener {

    private static final String DESTINATION_PREFIX = "/sub/direct-messages_";

    private final SimpMessagingTemplate messagingTemplate;

    @KafkaListener(topics = "dm-broadcast", groupId = "#{@realtimeConsumerGroupId}")
    public void onMessage(DirectMessageBroadcastMessage message) {
        String destination = DESTINATION_PREFIX + message.dmKey();
        messagingTemplate.convertAndSend(destination, message);
    }
}
