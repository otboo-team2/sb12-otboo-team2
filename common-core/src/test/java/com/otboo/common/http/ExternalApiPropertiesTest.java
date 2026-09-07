package com.otboo.common.http;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ExternalApiPropertiesTest {

    private static ApiSettings only(Duration readTimeout, Integer maxRetries) {
        return new ApiSettings(null, readTimeout, maxRetries, null, null);
    }

    @Test
    @DisplayName("설정이 없는 API 는 기본값으로 동작한다 — 블록을 빠뜨려도 앱은 뜬다")
    void 설정이_없으면_기본값() {
        ExternalApiProperties properties = new ExternalApiProperties(null, null);

        ApiSettings settings = properties.forApi("아무거나");

        assertThat(settings).isEqualTo(ApiSettings.DEFAULTS);
        assertThat(settings.maxRetries()).isZero();
    }

    @Test
    @DisplayName("지정한 항목만 덮고 나머지는 defaults 를 따른다")
    void 부분_재정의() {
        ExternalApiProperties properties = new ExternalApiProperties(
                only(Duration.ofSeconds(20), null),
                Map.of("llm", only(Duration.ofSeconds(60), 1)));

        ApiSettings llm = properties.forApi("llm");

        assertThat(llm.readTimeout()).isEqualTo(Duration.ofSeconds(60));   // API 값
        assertThat(llm.maxRetries()).isEqualTo(1);                         // API 값
        assertThat(llm.connectTimeout())
                .isEqualTo(ApiSettings.DEFAULTS.connectTimeout());          // 전역 기본값
    }

    @Test
    @DisplayName("defaults 에만 있는 값도 API 별 설정에 적용된다")
    void defaults_가_전파된다() {
        ExternalApiProperties properties = new ExternalApiProperties(
                only(Duration.ofSeconds(20), 3),
                Map.of("weather", only(Duration.ofSeconds(5), null)));

        assertThat(properties.forApi("weather").maxRetries()).isEqualTo(3);
    }

    @Test
    @DisplayName("음수 재시도는 0 으로 막는다")
    void 음수_재시도는_0() {
        assertThat(only(null, -5).maxRetries()).isZero();
    }
}
