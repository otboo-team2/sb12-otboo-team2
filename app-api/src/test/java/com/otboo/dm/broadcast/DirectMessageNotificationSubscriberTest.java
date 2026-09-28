package com.otboo.dm.broadcast;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DirectMessageNotificationSubscriberTest {

    private final DirectMessageEventPublisher directMessageEventPublisher = mock(DirectMessageEventPublisher.class);
    private final DirectMessageNotificationSubscriber subscriber =
        new DirectMessageNotificationSubscriber(directMessageEventPublisher);

    @Test
    void delegatesToEventPublisher() {
        DirectMessageBroadcastMessage message = new DirectMessageBroadcastMessage(
            UUID.randomUUID(), Instant.now(), "dmKey",
            new DirectMessageBroadcastMessage.UserSummary(UUID.randomUUID(), "보낸사람", null),
            new DirectMessageBroadcastMessage.UserSummary(UUID.randomUUID(), "받는사람", null),
            "내용");

        subscriber.onMessage(message);

        verify(directMessageEventPublisher).publish(message);
    }
}
