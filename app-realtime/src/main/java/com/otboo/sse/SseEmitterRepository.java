package com.otboo.sse;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Repository
public class SseEmitterRepository {

    private final Map<UUID, SseEmitter> emitters = new ConcurrentHashMap<>();

    public void save(UUID userId, SseEmitter emitter) {
        SseEmitter previous = emitters.put(userId, emitter);
        if (previous != null) {
            previous.complete();
        }
    }

    public void remove(UUID userId, SseEmitter emitter) {
        emitters.remove(userId, emitter);
    }

    public SseEmitter findByUserId(UUID userId) {
        return emitters.get(userId);
    }

    public Collection<SseEmitter> findAll() {
        return emitters.values();
    }
}
