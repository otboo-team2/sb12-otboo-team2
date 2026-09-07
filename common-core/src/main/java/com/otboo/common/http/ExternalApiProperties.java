package com.otboo.common.http;

import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 외부 API 호출 정책 설정.
 *
 * <pre>
 * otboo:
 *   external-api:
 *     defaults:              # 모든 API 에 적용되는 기본값
 *       read-timeout: 10s
 *       max-retries: 0
 *     apis:
 *       weather:             # 여기 이름이 factory.create("weather", ...) 의 이름과 같아야 한다
 *         read-timeout: 5s
 *         max-retries: 2
 * </pre>
 *
 * <p>각 파트는 자기 API 블록만 추가하면 된다. 공통 자바 코드는 건드리지 않는다.
 *
 * @param defaults 지정하지 않은 항목의 기본값
 * @param apis     API 이름 → 개별 설정. 지정한 항목만 {@code defaults} 를 덮는다.
 */
@ConfigurationProperties(prefix = "otboo.external-api")
public record ExternalApiProperties(
        ApiSettings defaults,
        Map<String, ApiSettings> apis
) {

    public ExternalApiProperties {
        defaults = defaults == null
                ? ApiSettings.DEFAULTS
                : defaults.withFallback(ApiSettings.DEFAULTS);
        apis = apis == null ? Map.of() : Map.copyOf(apis);
    }

    /** 설정이 없는 API 는 기본값으로 동작한다. 설정을 빠뜨렸다고 앱이 안 뜨지는 않는다. */
    public ApiSettings forApi(String apiName) {
        ApiSettings override = apis.get(apiName);
        return override == null ? defaults : override.withFallback(defaults);
    }
}
