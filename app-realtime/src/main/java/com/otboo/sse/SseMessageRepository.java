package com.otboo.sse;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Repository;

@Repository
public class SseMessageRepository {

    private static final Duration BUFFER_TTL = Duration.ofHours(1);

    private final int perUserCapacity;

    private final Map<UUID, UserBuffer> buffers = new ConcurrentHashMap<>();

    public SseMessageRepository(
        @Value("${sse.event-queue-capacity}") int perUserCapacity
    ) {
        if (perUserCapacity <= 0) {
            throw new IllegalArgumentException("sse.event-queue-capacity는 양수여야 합니다.");
        }
        this.perUserCapacity = perUserCapacity;
    }

    public SseMessage save(SseMessage message) {
        buffers.compute(message.receiverId(), (id, existing) -> {
            UserBuffer buffer = existing != null ? existing : new UserBuffer(perUserCapacity);
            buffer.add(message);
            return buffer;
        });
        return message;
    }

    public List<SseMessage> findAllAfter(UUID lastEventId, UUID receiverId) {
        UserBuffer buffer = buffers.get(receiverId);
        if (buffer == null) {
            return List.of();
        }

        List<SseMessage> result = buffer.after(lastEventId);
        return result != null ? result : List.of();
    }

    @Scheduled(fixedRate = 30, timeUnit = TimeUnit.MINUTES)
    public void evictStaleBuffers() {
        Instant cutoff = Instant.now().minus(BUFFER_TTL);
        for (UUID receiverId : buffers.keySet()) {
            buffers.computeIfPresent(receiverId, (id, buffer) ->
                buffer.lastSavedAt().isBefore(cutoff) ? null : buffer);
        }
    }

    private static final class UserBuffer {
        private final Deque<SseMessage> deque;
        private final int capacity;
        private Instant lastSavedAt = Instant.now();

        UserBuffer(int capacity) {
            this.capacity = capacity;
            this.deque = new ArrayDeque<>(capacity);
        }

        synchronized void add(SseMessage message) {
            while (deque.size() >= capacity) {
                deque.removeFirst();
            }
            deque.addLast(message);
            lastSavedAt = Instant.now();
        }

        synchronized List<SseMessage> after(UUID lastEventId) {
            List<SseMessage> result = new ArrayList<>();
            boolean found = false;
            for (SseMessage message : deque) {
                if (found) {
                    result.add(message);
                } else if (message.eventId().equals(lastEventId)) {
                    found = true;
                }
            }
            return found ? result : null;
        }

        synchronized Instant lastSavedAt() {
            return lastSavedAt;
        }
    }
}
