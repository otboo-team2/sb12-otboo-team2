package com.otboo.common.http;

import java.net.http.HttpClient;
import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * API 이름에 맞는 {@link ExternalApiClient} 를 만들어준다.
 *
 * <p>각 파트는 자기 클라이언트에서 이렇게 쓴다.
 * <pre>
 * &#64;Component
 * public class OpenWeatherMapClient {
 *
 *     private final ExternalApiClient api;
 *
 *     public OpenWeatherMapClient(ExternalApiClientFactory factory,
 *                                 &#64;Value("${otboo.weather.api-key}") String apiKey) {
 *         this.api = factory.create("weather", builder -&gt; builder
 *                 .baseUrl("https://api.openweathermap.org")
 *                 .defaultHeader("X-Api-Key", apiKey));   // 가능하면 키는 헤더로
 *     }
 *
 *     public OwmResponse fetch(double lat, double lon) {
 *         return api.get("/data/3.0/onecall?lat=%s&amp;lon=%s".formatted(lat, lon), OwmResponse.class);
 *     }
 * }
 * </pre>
 *
 * <p>{@code "weather"} 는 {@code otboo.external-api.apis.weather} 설정 블록을 찾는 이름이다.
 * 블록이 없으면 기본값(연결 3초 · 읽기 10초 · 재시도 0)으로 동작한다.
 */
@Component
public class ExternalApiClientFactory {

    private final ExternalApiProperties properties;
    private final Clock clock;

    /** API 이름별 호출 카운터. 같은 이름으로 클라이언트를 여러 번 만들어도 상한은 하나로 센다. */
    private final Map<String, DailyCallCounter> counters = new ConcurrentHashMap<>();

    public ExternalApiClientFactory(ExternalApiProperties properties) {
        this.properties = properties;
        // 상한의 하루 경계는 UTC 다. 프로젝트 전체 기준과 맞춘다.
        this.clock = Clock.systemUTC();
    }

    public ExternalApiClient create(String apiName, String baseUrl) {
        return create(apiName, HttpClient.Redirect.NORMAL, builder -> builder.baseUrl(baseUrl));
    }

    /** 기본 헤더 등을 직접 붙여야 할 때 쓴다. 타임아웃은 설정에서 이미 적용된 상태로 넘어온다. */
    public ExternalApiClient create(String apiName, Consumer<RestClient.Builder> customizer) {
        return create(apiName, HttpClient.Redirect.NORMAL, customizer);
    }

    /** 리다이렉트 정책을 호출별로 선택할 때 쓴다. */
    public ExternalApiClient create(
            String apiName,
            HttpClient.Redirect redirect,
            Consumer<RestClient.Builder> customizer
    ) {
        ApiSettings settings = properties.forApi(apiName);

        // JDK HttpClient 는 연결 타임아웃을, Spring 의 팩토리가 읽기 타임아웃을 담당한다.
        // 둘 다 걸어야 한다. 읽기 타임아웃만 걸면 연결 단계에서 무한 대기가 남는다.
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(settings.connectTimeout())
                .followRedirects(redirect)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(settings.readTimeout());

        RestClient.Builder builder = RestClient.builder().requestFactory(requestFactory);
        customizer.accept(builder);

        return new ExternalApiClient(apiName, builder.build(), settings,
                counters.computeIfAbsent(apiName, name -> new DailyCallCounter(clock)));
    }
}
