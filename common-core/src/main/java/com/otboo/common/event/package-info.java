/**
 * 도메인 이벤트.
 *
 * <h2>발행하는 쪽 — 저장 로직 끝에 한 줄</h2>
 * <pre>
 * private final ApplicationEventPublisher eventPublisher;
 *
 * {@code @Transactional}
 * public ClothesAttributeDefDto create(ClothesAttributeDefCreateRequest request) {
 *     var saved = repository.save(...);
 *     eventPublisher.publishEvent(ClothesAttributeAddedEvent.of(saved.getId(), saved.getName()));
 *     return ClothesAttributeDefDto.from(saved);
 * }
 * </pre>
 *
 * {@code of(...)} 팩토리가 {@code eventId} 와 {@code occurredAt} 을 채운다. 직접 만들지 않아도 된다.
 *
 * <h2>왜 common-core 에 있나</h2>
 * 발행자는 {@code app-api}, 소비자는 {@code app-realtime} 이고 <b>서로 다른 컨테이너</b>다.
 * 이벤트가 프로세스를 넘어가려면 양쪽이 같은 클래스를 봐야 한다.
 *
 * <h2>수신자는 알림 파트가 정한다</h2>
 * 이벤트는 <b>무슨 일이 일어났는지</b>만 담는다. 누구에게 알릴지는 소비자가 결정한다.
 * 특히 팔로워 전원처럼 <b>이벤트 1개 → 알림 N개</b>인 경우가 있어서,
 * 발행하는 도메인이 수신자를 조회하면 안 된다.
 *
 * <h2>주의</h2>
 * <ul>
 *   <li>{@code record} 로 만들고 필드는 값 타입만. <b>엔티티를 통째로 담지 말 것</b> —
 *       직렬화가 깨지고 common-core 가 남의 도메인을 알게 된다.</li>
 *   <li>이 패키지 때문에 common-core 에 새 의존성을 추가하지 말 것.</li>
 *   <li>Spring 내장 {@code ApplicationEventPublisher} 는 <b>같은 JVM 안에서만</b> 동작한다.
 *       realtime 까지 보내는 브릿지(AFTER_COMMIT 리스너 → Redis Pub/Sub)는 공통 파트에서 붙인다.
 *       도메인 개발자는 그대로 한 줄만 쓰면 된다.</li>
 * </ul>
 */
package com.otboo.common.event;
