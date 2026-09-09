package com.otboo.sse;

import com.otboo.notification.broadcast.NotificationBroadcastMessage;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Slf4j
@Service
@RequiredArgsConstructor
public class SseDeliveryService {

    private static final String EVENT_NAME = "notifications";
    private static final String PING_EVENT_NAME = "ping";

    @Value("${sse.timeout}") // 기본 30분
    private long timeout;

    private final SseEmitterRepository sseEmitterRepository;
    private final SseMessageRepository sseMessageRepository;

    public SseEmitter connect(UUID receiverId, UUID lastEventId) {
        SseEmitter emitter = new SseEmitter(timeout);

        emitter.onCompletion(() -> sseEmitterRepository.remove(receiverId, emitter));
        emitter.onTimeout(() -> sseEmitterRepository.remove(receiverId, emitter));
        emitter.onError(e -> sseEmitterRepository.remove(receiverId, emitter));

        sseEmitterRepository.save(receiverId, emitter);

        if (lastEventId != null) {
            sendMissedMessage(receiverId, emitter, lastEventId);
        } else {
            ping(emitter);
        }

        return emitter;
    }

    public void deliver(NotificationBroadcastMessage data) {
        SseMessage message = sseMessageRepository.save(SseMessage.of(data));
        SseEmitter emitter = sseEmitterRepository.findByUserId(data.receiverId());

        if (emitter != null) {
            sendTo(emitter, message);
        }
    }

    private void sendMissedMessage(UUID receiverId, SseEmitter emitter, UUID lastEventId) {
        sseMessageRepository.findAllAfter(lastEventId, receiverId)
            .forEach(message -> sendTo(emitter, message));
    }

    private void sendTo(SseEmitter emitter, SseMessage message) {
        try {
            emitter.send(SseEmitter.event()
                .id(message.eventId().toString())
                .name(EVENT_NAME)
                .data(message.data()));
        } catch (Exception e) {
            // log.debug("SSE 전송 실패, emitter 제거", e);
            sseEmitterRepository.remove(message.receiverId(), emitter);
        }
    }

    @Scheduled(fixedRateString = "${sse.heartbeat-interval}")
    public void sendHeartbeat() {
        for (SseEmitter emitter : sseEmitterRepository.findAll()) {
            if (!ping(emitter)) {
                emitter.complete();
            }
        }
    }

    private boolean ping(SseEmitter emitter) {
        try {
            emitter.send(SseEmitter.event().name(PING_EVENT_NAME).build());
            return true;
        } catch (Exception e) {
            // log.debug("ping 실패, emitter 정리: {}", e.getMessage());
            return false;
        }
    }
}
