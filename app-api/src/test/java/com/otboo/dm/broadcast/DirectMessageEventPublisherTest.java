package com.otboo.dm.broadcast;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.otboo.common.event.DirectMessageReceivedEvent;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

class DirectMessageEventPublisherTest {

    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final DirectMessageEventPublisher publisher = new DirectMessageEventPublisher(eventPublisher);

    @Test
    void publishesDirectMessageReceivedEventFromBroadcastMessage() {
        UUID senderId = UUID.randomUUID();
        UUID receiverId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        DirectMessageBroadcastMessage message = new DirectMessageBroadcastMessage(
            messageId, Instant.now(), "dmKey",
            new DirectMessageBroadcastMessage.UserSummary(senderId, "보낸사람", null),
            new DirectMessageBroadcastMessage.UserSummary(receiverId, "받는사람", null),
            "내용");

        publisher.publish(message);

        ArgumentCaptor<DirectMessageReceivedEvent> captor =
            ArgumentCaptor.forClass(DirectMessageReceivedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());

        DirectMessageReceivedEvent event = captor.getValue();
        assertThat(event.senderId()).isEqualTo(senderId);
        assertThat(event.receiverId()).isEqualTo(receiverId);
        assertThat(event.directMessageId()).isEqualTo(messageId);
        assertThat(event.content()).isEqualTo("내용");
    }
}
