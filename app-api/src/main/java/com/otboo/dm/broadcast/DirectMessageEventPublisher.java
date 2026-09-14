package com.otboo.dm.broadcast;

import com.otboo.common.event.DirectMessageReceivedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DirectMessageEventPublisher {

    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public void publish(DirectMessageBroadcastMessage message) {
        eventPublisher.publishEvent(DirectMessageReceivedEvent.of(
            message.sender().userId(), message.receiver().userId(), message.id()));
    }
}
