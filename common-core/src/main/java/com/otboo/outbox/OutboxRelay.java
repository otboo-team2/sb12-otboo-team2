package com.otboo.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * outbox 의 미발행 메시지를 Kafka 로 보낸다.
 *
 * <p>커밋 직후 한 번 깨어나고(onAppended), 5초마다 다시 돈다. Kafka 가 멈춰 있던 동안 쌓인
 * 메시지는 복구 후 다음 주기에 나간다. 전송 성공 후 기록 전에 죽으면 한 번 더 보낸다
 * (at-least-once). 중복은 받는 쪽이 메시지 id 로 거른다.
 */
@Slf4j
@Component
@ConditionalOnClass(name = "org.springframework.kafka.core.KafkaTemplate")
public class OutboxRelay {

    private static final int BATCH_SIZE = 100;
    // producer 의 delivery.timeout.ms(5초)보다 길게 기다려야 성공/실패가 확정된다.
    // 더 짧으면 "실패"로 보고 다시 보냈는데 앞의 것도 나중에 전송돼 중복이 생긴다.
    private static final long SEND_TIMEOUT_MS = 10_000;
    private static final int CLEANUP_LIMIT = 10_000;
    private static final Duration RETENTION = Duration.ofDays(7);

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    public OutboxRelay(
        OutboxEventRepository outboxEventRepository,
        KafkaTemplate<String, Object> kafkaTemplate,
        ObjectMapper objectMapper,
        PlatformTransactionManager transactionManager
    ) {
        this.outboxEventRepository = outboxEventRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    /** 전용 실행기(1개 실행 + 1개 대기, 넘치면 버림)라 공용 비동기 풀을 막지 않는다. */
    @Async("outboxRelayExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAppended(OutboxAppendedEvent event) {
        relay();
    }

    @Scheduled(fixedDelayString = "${otboo.outbox.relay-interval:5000}")
    public void relay() {
        Integer published;
        do {
            published = transactionTemplate.execute(status -> relayBatch());
        } while (published != null && published == BATCH_SIZE);   // 꽉 찼으면 더 남아 있다
    }

    @Scheduled(cron = "${otboo.outbox.cleanup-cron:0 0 4 * * *}")
    public void deletePublished() {
        Instant before = Instant.now().minus(RETENTION);
        Integer deleted;
        do {
            deleted = transactionTemplate.execute(
                status -> outboxEventRepository.deletePublishedBefore(before, CLEANUP_LIMIT));
        } while (deleted != null && deleted == CLEANUP_LIMIT);
    }

    /** 역직렬화까지 마친, 보낼 준비가 된 메시지 */
    private record Prepared(OutboxEvent event, Object payload) {
    }

    private int relayBatch() {
        List<OutboxEvent> batch = outboxEventRepository.findUnpublishedForUpdate(BATCH_SIZE);
        if (batch.isEmpty()) {
            return 0;
        }
        Instant now = Instant.now();

        // 영구 실패(역직렬화 불가 등)는 먼저 걸러 failed_at 으로 표시한다.
        // 남겨 두면 매 주기 맨 앞에서 실패해 뒤의 정상 메시지까지 영원히 막는다.
        List<Prepared> sendable = new ArrayList<>();
        for (OutboxEvent event : batch) {
            try {
                sendable.add(new Prepared(event, deserialize(event)));
            } catch (Exception e) {
                // 예외 메시지에 원본 JSON(알림 내용)이 섞일 수 있어 예외 종류만 남긴다
                String reason = e.getClass().getSimpleName();
                log.error("outbox_event_unpublishable id={} type={} reason={}",
                    event.getId(), event.getPayloadType(), reason);
                event.markFailed(now, reason);
            }
        }
        int handled = batch.size() - sendable.size();
        if (sendable.isEmpty()) {
            return handled;
        }

        // 첫 건은 ack 까지 확인한다. Kafka 가 죽어 있으면 여기서 끊어서,
        // 나머지가 한 건씩 타임아웃을 기다리며 DB 잠금을 오래 쥐지 않게 한다.
        Prepared first = sendable.get(0);
        if (!await(send(first), first.event())) {
            return handled;
        }
        first.event().markPublished(now);
        handled++;

        List<CompletableFuture<SendResult<String, Object>>> rest = sendable.subList(1, sendable.size()).stream()
            .map(this::send)
            .toList();
        for (int i = 0; i < rest.size(); i++) {
            OutboxEvent event = sendable.get(i + 1).event();
            if (!await(rest.get(i), event)) {
                break;   // 여기부터는 NULL 로 남아 다음 주기에 다시 시도된다
            }
            event.markPublished(now);
            handled++;
        }
        return handled;
    }

    private Object deserialize(OutboxEvent event) throws Exception {
        return objectMapper.readValue(event.getPayload(), payloadClass(event));
    }

    private CompletableFuture<SendResult<String, Object>> send(Prepared prepared) {
        OutboxEvent event = prepared.event();
        try {
            return kafkaTemplate.send(event.getTopic(), event.getMessageKey(), prepared.payload());
        } catch (Exception e) {
            // Kafka 가 죽어 있으면 send() 가 max.block.ms 뒤 바로 예외를 던질 수 있다 → 일시적 실패로 취급
            return CompletableFuture.failedFuture(e);
        }
    }

    private Class<?> payloadClass(OutboxEvent event) throws ClassNotFoundException {
        String type = event.getPayloadType();
        if (!type.startsWith("com.otboo.")) {
            throw new IllegalStateException("untrusted outbox payload type: " + type);
        }
        return Class.forName(type);
    }

    private boolean await(CompletableFuture<?> future, OutboxEvent event) {
        try {
            future.get(SEND_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            log.warn("outbox_publish_failed id={} topic={} reason={}", event.getId(), event.getTopic(), e.toString());
            return false;
        }
    }
}
