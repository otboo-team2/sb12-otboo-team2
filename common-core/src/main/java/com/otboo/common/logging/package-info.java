/**
 * 로깅 컨벤션. <b>각 파트는 이 문서만 읽고 자기 코드에 로그를 넣으면 된다.</b>
 *
 * <h2>1. 형식 — {@code 이벤트이름 키=값 키=값}</h2>
 * 문장이 아니라 <b>검색 가능한 키=값</b>으로 쓴다.
 * <pre>
 * ❌  log.info("사용자 " + userId + "가 피드를 만들었습니다");
 * ✅  log.info("feed_created feedId={} clothesCount={}", feedId, clothesCount);
 * </pre>
 * 문장으로 쓰면 나중에 "피드 생성이 하루에 몇 건이지"를 셀 수 없다.
 * 키=값이면 {@code grep 'feed_created' | wc -l} 로 끝난다.
 *
 * <p><b>문자열을 {@code +} 로 잇지 않는다.</b> {@code {}} 를 쓰면 그 레벨이 꺼져 있을 때
 * 문자열을 아예 만들지 않는다. DEBUG 로그가 운영에서 공짜가 되는 이유다.
 *
 * <h2>2. requestId 는 자동으로 붙는다</h2>
 * {@link com.otboo.common.logging.RequestLoggingFilter} 가 요청마다 넣어주므로
 * <b>각 파트가 신경 쓸 필요가 없다.</b> 로그인한 요청이면 {@code userId} 도 함께 붙는다.
 * <pre>
 * 14:23:01.482 INFO  [3f2a9c1b] [7d1e-user] c.o.f.s.FeedService : feed_created feedId=... clothesCount=3
 *                     └ requestId  └ userId
 * </pre>
 * 그래서 <b>로그 메시지에 userId 를 또 넣지 않는다.</b> 중복이다.
 *
 * <h2>3. 레벨 기준 — "누가 언제 보는가"로 정한다</h2>
 * <table border="1">
 *   <caption>레벨</caption>
 *   <tr><th>레벨</th><th>뜻</th><th>예</th></tr>
 *   <tr><td>ERROR</td><td>사람이 지금 봐야 한다. 자동 복구 안 됨</td>
 *       <td>DB 접속 끊김, 결제 정합성 깨짐</td></tr>
 *   <tr><td>WARN</td><td>이상하지만 서비스는 계속된다</td>
 *       <td>외부 API 재시도, 이미지 삭제 실패, 5xx 응답</td></tr>
 *   <tr><td>INFO</td><td>비즈니스 사건. 나중에 세거나 추적할 값어치가 있다</td>
 *       <td>가입, 피드 생성, 권한 변경, 외부 API 호출</td></tr>
 *   <tr><td>DEBUG</td><td>개발할 때만 본다. 운영에서 꺼진다</td>
 *       <td>분기 조건, 중간 계산값</td></tr>
 * </table>
 *
 * <p><b>잡히는 예외를 전부 ERROR 로 찍지 않는다.</b> 사용자가 잘못 입력해서 나는 400 은
 * 로그를 남길 일이 아니다. {@code GlobalExceptionHandler} 가 이미 응답으로 처리한다.
 * ERROR 가 흔해지면 아무도 안 본다.
 *
 * <h2>4. 예외는 마지막 인자로 넘긴다</h2>
 * <pre>
 * ❌  log.warn("이미지 삭제 실패: " + e.getMessage());     // 스택트레이스가 사라진다
 * ✅  log.warn("image_delete_failed url={}", url, e);      // 마지막 인자가 예외면 스택이 찍힌다
 * </pre>
 *
 * <h2>5. 절대 찍지 않는 것</h2>
 * 이 레포는 <b>Public</b> 이고 로그는 발표 자료·스크린샷으로 나간다.
 * <ul>
 *   <li>비밀번호 — 인코딩 전이든 후든</li>
 *   <li>액세스·리프레시 토큰, API 키 — 앞 몇 글자도 안 된다</li>
 *   <li>요청 본문 전체 — 위 세 개가 섞여 들어온다</li>
 *   <li>이메일 전체 주소 — 필요하면 userId 를 쓴다</li>
 * </ul>
 *
 * <h2>6. 계층별 예시</h2>
 *
 * <h3>컨트롤러 — 로그를 쓰지 않는다</h3>
 * <pre>
 * &#64;PostMapping
 * public ResponseEntity&lt;FeedDto&gt; create(&#64;LoginUser AuthPrincipal me,
 *                                       &#64;Valid &#64;RequestBody FeedCreateRequest request) {
 *     // 요청·응답·소요시간·상태코드는 RequestLoggingFilter 가 이미 남긴다.
 *     // 여기서 또 찍으면 같은 내용이 두 줄이 된다.
 *     return ResponseEntity.status(CREATED).body(feedService.create(me, request));
 * }
 * </pre>
 *
 * <h3>서비스 — 비즈니스 사건만 남긴다</h3>
 * <pre>
 * &#64;Slf4j
 * &#64;Service
 * public class FeedService {
 *
 *     &#64;Transactional
 *     public FeedDto create(AuthPrincipal me, FeedCreateRequest request) {
 *         Feed feed = feedRepository.save(...);
 *
 *         // 성공한 사건. "무엇이 일어났는지"와 나중에 셀 수 있는 값을 남긴다.
 *         log.info("feed_created feedId={} clothesCount={}",
 *                 feed.getId(), request.clothesIds().size());
 *
 *         return viewLoader.loadOne(feed.getId(), me.userId());
 *     }
 *
 *     &#64;Transactional
 *     public void delete(AuthPrincipal me, UUID feedId) {
 *         try {
 *             feedRepository.delete(findEditableFeed(me, feedId));
 *         } catch (DataIntegrityViolationException e) {
 *             // 서비스는 계속된다. 사용자에게는 409 로 나간다. → WARN
 *             log.warn("feed_delete_blocked feedId={}", feedId, e);
 *             throw new BusinessException(FeedErrorCode.IN_USE, e);
 *         }
 *     }
 * }
 * </pre>
 *
 * <p><b>검증 실패는 서비스가 로그를 남기지 않는다.</b> 예외를 던지면
 * {@code GlobalExceptionHandler} 가 응답을 만들고, 상태코드는 필터가 기록한다.
 *
 * <h3>외부 호출 — 각자 찍지 않는다</h3>
 * <pre>
 * // ExternalApiClient 가 이미 남긴다:
 * //   external_call api=llm endpoint=POST /v1/messages result=ok attempt=1 elapsed_ms=2840
 * OwmResponse response = api.get("/data/3.0/onecall?...", OwmResponse.class);
 * </pre>
 * 소요 시간·재시도 횟수·성공 여부가 모든 외부 API 에서 같은 필드로 나온다.
 * <b>이 값이 발표용 성능 측정 자료가 된다</b> — 나중에 따로 만들 필요가 없다.
 *
 * <h3>배치 — 건별이 아니라 요약을 남긴다</h3>
 * <pre>
 * log.info("weather_collect_done regions={} saved={} skipped={} elapsed_ms={}",
 *         regions.size(), saved, skipped, elapsed);
 * </pre>
 * 격자 하나마다 한 줄씩 찍으면 수천 줄이 되고, 정작 "몇 건 실패했나"를 못 센다.
 *
 * <h2>7. 이벤트 이름 짓기</h2>
 * {@code 명사_과거분사} 로 통일한다. 소문자 + 밑줄.
 * <pre>
 * feed_created   user_locked   clothes_deleted   weather_collect_done
 * </pre>
 * 실패는 {@code _failed} 나 {@code _blocked} 를 붙인다.
 * 도메인 접두사는 붙이지 않는다 — 로거 이름(클래스)에 이미 들어 있다.
 */
package com.otboo.common.logging;
