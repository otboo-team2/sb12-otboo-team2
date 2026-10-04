-- V7 — Kafka 발행 대기열 (Transactional Outbox)
--
-- 비즈니스 데이터와 "보낼 메시지"를 같은 트랜잭션에 저장한다. 커밋되면 메시지도 반드시 남아 있고,
-- OutboxRelay 가 Kafka 로 보낸 뒤 published_at 을 채운다. Kafka 가 멈춰 있어도 메시지는 여기서 기다린다.
CREATE TABLE outbox_events
(
    id           CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    topic        VARCHAR(100)                                   NOT NULL,
    message_key  VARCHAR(100)                                   NULL COMMENT '같은 key 는 같은 파티션 → 순서 보장',
    payload_type VARCHAR(200)                                   NOT NULL COMMENT '역직렬화할 클래스 이름',
    payload      VARCHAR(4000)                                  NOT NULL COMMENT '메시지 JSON',
    published_at DATETIME(6)                                    NULL COMMENT 'NULL 이면 아직 안 보냄',
    failed_at    DATETIME(6)                                    NULL COMMENT '영구 실패(역직렬화 불가 등). 재시도하지 않는다',
    last_error   VARCHAR(500)                                   NULL COMMENT '영구 실패 사유',
    created_at   DATETIME(6)                                    NOT NULL,
    updated_at   DATETIME(6)                                    NOT NULL,
    PRIMARY KEY (id),
    KEY idx_outbox_events_unpublished (published_at, failed_at, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT 'Kafka 발행 대기열 (Transactional Outbox)';
