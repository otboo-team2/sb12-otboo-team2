package com.otboo.outbox;

import com.otboo.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "outbox_events")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxEvent extends BaseEntity {

    @Column(nullable = false, length = 100)
    private String topic;

    @Column(name = "message_key", length = 100)
    private String messageKey;

    @Column(name = "payload_type", nullable = false, length = 200)
    private String payloadType;

    @Column(nullable = false, length = 4000)
    private String payload;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "failed_at")
    private Instant failedAt;

    @Column(name = "last_error", length = 500)
    private String lastError;

    private OutboxEvent(String topic, String messageKey, String payloadType, String payload) {
        this.topic = topic;
        this.messageKey = messageKey;
        this.payloadType = payloadType;
        this.payload = payload;
    }

    public static OutboxEvent of(String topic, String messageKey, String payloadType, String payload) {
        return new OutboxEvent(topic, messageKey, payloadType, payload);
    }

    public void markPublished(Instant at) {
        this.publishedAt = at;
    }

    public void markFailed(Instant at, String error) {
        this.failedAt = at;
        this.lastError = error == null ? null : error.substring(0, Math.min(error.length(), 500));
    }
}
