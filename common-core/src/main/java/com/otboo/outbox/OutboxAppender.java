package com.otboo.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 보낼 메시지를 outbox 에 등록한다. 반드시 비즈니스 트랜잭션 안에서 불러야 한다(MANDATORY) —
 * 그래야 데이터 저장과 메시지 등록이 함께 커밋되거나 함께 롤백된다.
 */
@Component
@RequiredArgsConstructor
public class OutboxAppender {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(propagation = Propagation.MANDATORY)
    public void append(String topic, String key, Object payload) {
        outboxEventRepository.save(toEvent(topic, key, payload));
        eventPublisher.publishEvent(new OutboxAppendedEvent());
    }

    /** 여러 건을 한 번에 등록한다. 커밋 후 Relay 는 한 번만 깨운다. */
    @Transactional(propagation = Propagation.MANDATORY)
    public <T> void appendAll(String topic, List<T> payloads, Function<T, String> keyOf) {
        outboxEventRepository.saveAll(payloads.stream()
            .map(payload -> toEvent(topic, keyOf.apply(payload), payload))
            .toList());
        eventPublisher.publishEvent(new OutboxAppendedEvent());
    }

    private OutboxEvent toEvent(String topic, String key, Object payload) {
        try {
            return OutboxEvent.of(topic, key, payload.getClass().getName(),
                objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("outbox payload serialization failed: " + payload.getClass(), e);
        }
    }
}
