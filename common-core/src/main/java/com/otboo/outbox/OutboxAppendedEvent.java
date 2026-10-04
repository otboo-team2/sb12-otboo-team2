package com.otboo.outbox;

/** outbox 에 메시지가 등록됐다는 신호. 커밋 직후 Relay 를 깨우는 용도라 내용이 없다. */
public record OutboxAppendedEvent() {
}
