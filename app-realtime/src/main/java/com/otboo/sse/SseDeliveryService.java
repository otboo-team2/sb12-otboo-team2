package com.otboo.sse;

import com.otboo.notification.broadcast.NotificationBroadcastMessage;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
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

    @Qualifier("heartbeatExecutor")
    private final Executor heartbeatExecutor;

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
            sseEmitterRepository.remove(message.receiverId(), emitter);
        }
    }

    @Scheduled(fixedRateString = "${sse.heartbeat-interval}")
    public void sendHeartbeat() {
        List<CompletableFuture<Void>> futures = sseEmitterRepository.findAll().stream()
            .map(this::submitPing)
            .filter(Objects::nonNull)
            .toList();
        futures.forEach(CompletableFuture::join);
    }

    private CompletableFuture<Void> submitPing(SseEmitter emitter) {
        try {
            return CompletableFuture.runAsync(() -> pingOrComplete(emitter), heartbeatExecutor);
        } catch (RejectedExecutionException e) {
            // 다음 하트비트(20초 뒤)에 다시 시도되니 무시
            return null;
        }
    }

    private void pingOrComplete(SseEmitter emitter) {
        if (!ping(emitter)) {
            try {
                emitter.complete();
            } catch (Exception e) {
                // 이미 끊긴 연결 정리 중 발생하는 예외는 무시
            }
        }
    }

    private boolean ping(SseEmitter emitter) {
        try {
            emitter.send(SseEmitter.event().name(PING_EVENT_NAME).build());
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
