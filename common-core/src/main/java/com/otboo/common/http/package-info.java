/**
 * 외부 API 호출 공통 배관.
 *
 * <h2>왜 있는가</h2>
 * 외부 의존이 5개다 — 날씨 · 링크 추출 · 피팅 AI · Pinterest · LLM.
 * 각자 {@code RestTemplate} 을 새로 만들면 타임아웃 · 재시도 · 에러 처리가 5가지가 되고,
 * 그중 하나라도 타임아웃이 빠지면 그 API 하나가 서비스 전체의 스레드를 물어버린다.
 *
 * <h2>어디까지가 공통인가</h2>
 * 공통은 <b>"어떻게 부르는가"</b> 만 안다. 상대가 어떤 API 인지 몰라도 동작해야 한다.
 * API 이름을 아는 코드가 이 패키지에 생기면 잘못 만든 것이다.
 * URL · API 키 · 응답 DTO · 파싱 · 캐시 · 실패 시 화면 처리는 전부 도메인 몫이다.
 *
 * <h2>쓰는 법</h2>
 * <pre>
 * &#64;Component
 * public class OpenWeatherMapClient {
 *
 *     private final ExternalApiClient api;
 *
 *     public OpenWeatherMapClient(ExternalApiClientFactory factory) {
 *         this.api = factory.create("weather", "https://api.openweathermap.org");
 *     }
 *
 *     public OwmResponse fetch(double lat, double lon) {
 *         return api.get("/data/2.5/forecast?lat=" + lat + "&amp;lon=" + lon, OwmResponse.class);
 *     }
 * }
 * </pre>
 *
 * <p><b>사용자 입력이 URI 에 들어가면 문자열로 이어 붙이지 않는다.</b> {@code &} · {@code +} 가 쿼리를 깨고,
 * 미리 인코딩하면 RestClient 가 한 번 더 인코딩한다. {@code exchange} 로 템플릿 변수를 넘긴다.
 * 로그에도 템플릿만 남아 입력값이 찍히지 않는다. 예시는 {@code com.otboo.pinterest.client.PinterestClient}.
 *
 * <h2>설정</h2>
 * 자바 코드는 하나지만 정책값은 API 마다 다르다. 그래서 값은 각 앱의
 * {@code application.yml} 에 둔다. <b>담당자는 자기 블록만 고치면 되고 공통 코드는 안 건드린다.</b>
 * <pre>
 * otboo:
 *   external-api:
 *     apis:
 *       weather:
 *         read-timeout: 5s
 *         max-retries: 2
 * </pre>
 *
 * <h2>기본값이 보수적인 이유</h2>
 * {@code max-retries} 기본값은 0 이다. 재시도가 안전한지는 API 담당자만 알 수 있고,
 * 틀렸을 때 대가가 비대칭이기 때문이다 — 재시도를 안 해서 생기는 손해는 "한 번 더 눌러주세요"지만,
 * 하면 안 되는 API 에 재시도를 걸면 <b>과금이 중복되고 결과물이 두 개 생긴다.</b>
 * 안전한 API 만 담당자가 값을 올린다.
 *
 * @see com.otboo.common.http.ExternalApiClient
 * @see com.otboo.common.http.ExternalApiClientFactory
 */
package com.otboo.common.http;
