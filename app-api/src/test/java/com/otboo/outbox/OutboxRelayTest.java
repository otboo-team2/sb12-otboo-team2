package com.otboo.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class OutboxRelayTest {

    record TestPayload(String text) {
    }

    @Mock OutboxEventRepository repository;
    @Mock KafkaTemplate<String, Object> kafkaTemplate;
    @Mock TransactionTemplate transactionTemplate;
    OutboxRelay relay;

    @BeforeEach
    void setUp() {
        relay = new OutboxRelay(repository, kafkaTemplate, new ObjectMapper(), transactionTemplate);
        when(transactionTemplate.execute(any())).thenAnswer(inv ->
            inv.<TransactionCallback<?>>getArgument(0).doInTransaction(mock(TransactionStatus.class)));
    }

    private OutboxEvent event() {
        return OutboxEvent.of("notification-broadcast", "key", TestPayload.class.getName(), "{\"text\":\"hi\"}");
    }

    @Test
    @DisplayName("ack 를 받은 메시지는 발행 완료로 표시한다")
    void marksPublishedOnAck() {
        OutboxEvent first = event();
        OutboxEvent second = event();
        when(repository.findUnpublishedForUpdate(100)).thenReturn(List.of(first, second));
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(CompletableFuture.completedFuture(null));

        relay.relay();

        assertThat(first.getPublishedAt()).isNotNull();
        assertThat(second.getPublishedAt()).isNotNull();
    }

    @Test
    @DisplayName("첫 건 전송이 실패하면(Kafka 장애) 아무것도 표시하지 않는다")
    void leavesAllPendingWhenFirstSendFails() {
        OutboxEvent first = event();
        OutboxEvent second = event();
        when(repository.findUnpublishedForUpdate(100)).thenReturn(List.of(first, second));
        when(kafkaTemplate.send(anyString(), anyString(), any()))
            .thenReturn(CompletableFuture.failedFuture(new RuntimeException("broker down")));

        relay.relay();

        assertThat(first.getPublishedAt()).isNull();
        assertThat(second.getPublishedAt()).isNull();
    }

    @Test
    @DisplayName("역직렬화할 수 없는 메시지는 실패로 빼고 나머지는 보낸다")
    void skipsUnpublishableAndSendsRest() {
        OutboxEvent broken = OutboxEvent.of("notification-broadcast", "key", "java.lang.String", "\"x\"");
        OutboxEvent good = event();
        when(repository.findUnpublishedForUpdate(100)).thenReturn(List.of(broken, good));
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(CompletableFuture.completedFuture(null));

        relay.relay();

        assertThat(broken.getFailedAt()).isNotNull();
        assertThat(broken.getPublishedAt()).isNull();
        assertThat(good.getPublishedAt()).isNotNull();
    }
}
