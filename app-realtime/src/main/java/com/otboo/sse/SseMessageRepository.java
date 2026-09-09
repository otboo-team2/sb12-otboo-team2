package com.otboo.sse;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

@Repository
public class SseMessageRepository {

    @Value("${sse.event-queue-capacity}")
    private int eventQueueCapacity;

    private final ConcurrentLinkedDeque<UUID> eventIdQueue = new ConcurrentLinkedDeque<>();
    private final Map<UUID, SseMessage> messages = new ConcurrentHashMap<>();

    public synchronized SseMessage save(SseMessage message) {
        while (eventIdQueue.size() >= eventQueueCapacity) {
            UUID removed = eventIdQueue.removeFirst();
            messages.remove(removed);
        }
        eventIdQueue.addLast(message.eventId());
        messages.put(message.eventId(), message);

        return message;
    }

    public List<SseMessage> findAllAfter(UUID lastEventId, UUID receiverId) {
        return eventIdQueue.stream()
            .dropWhile(id -> !id.equals(lastEventId))
            .skip(1)
            .map(messages::get)
            .filter(message -> message != null && message.receiverId().equals(receiverId))
            .toList();
    }
}
