package com.otboo.dm.broadcast;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

class DmKafkaListenerTest {

    private final SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
    private final DmKafkaListener listener = new DmKafkaListener(messagingTemplate);

    @Test
    void sendsMessageToDestinationBuiltFromDmKey() {
        DirectMessageBroadcastMessage message = new DirectMessageBroadcastMessage(
            UUID.randomUUID(), Instant.now(), "aaaa_bbbb",
            new DirectMessageBroadcastMessage.UserSummary(UUID.randomUUID(), "보낸사람", null),
            new DirectMessageBroadcastMessage.UserSummary(UUID.randomUUID(), "받는사람", null),
            "내용");

        listener.onMessage(message);

        verify(messagingTemplate).convertAndSend("/sub/direct-messages_aaaa_bbbb", message);
    }
}
