package com.otboo.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.otboo.common.config.ExternalApiConfig;
import com.otboo.common.http.ApiSettings;
import com.otboo.common.http.ExternalApiProperties;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;

/**
 * application.yml 의 외부 API 설정이 실제로 바인딩되는지 본다.
 *
 * <p>이름을 잘못 적으면 스프링이 조용히 무시하고 기본값으로 동작한다.
 * 그러면 60초로 적어둔 LLM 타임아웃이 10초로 돌아 원인 모를 실패가 난다.
 * 담당자가 자기 블록을 추가할 때 이 테스트에 한 줄 얹으면 그 사고를 막는다.
 */
class ExternalApiSettingsBindingTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(ExternalApiConfig.class);

    private void withProperties(java.util.function.Consumer<ExternalApiProperties> assertion) {
        runner.run((AssertableApplicationContext context) ->
                assertion.accept(context.getBean(ExternalApiProperties.class)));
    }

    @Test
    @DisplayName("모든 외부 API 의 재시도 기본값은 0 이다")
    void 재시도는_전부_0() {
        withProperties(properties -> assertThat(properties.apis().keySet())
                .allSatisfy(name -> assertThat(properties.forApi(name).maxRetries())
                        .as("%s 의 재시도 횟수 — 올리려면 재호출이 안전한지 먼저 확인할 것", name)
                        .isZero()));
    }

    @Test
    @DisplayName("담당자가 적은 값이 그대로 들어온다")
    void 개별_설정이_바인딩된다() {
        withProperties(properties -> {
            assertThat(properties.forApi("llm").readTimeout()).isEqualTo(Duration.ofSeconds(60));
            assertThat(properties.forApi("llm").dailyLimit()).isEqualTo(500L);
            assertThat(properties.forApi("virtual-try-on").readTimeout())
                    .isEqualTo(Duration.ofSeconds(120));
        });
    }

    @Test
    @DisplayName("설정에 없는 API 도 기본값으로 뜬다 — 블록을 빠뜨려도 앱이 죽지 않는다")
    void 설정없는_api는_기본값() {
        withProperties(properties -> assertThat(properties.forApi("아직-안-붙인-api"))
                .isEqualTo(new ApiSettings(Duration.ofSeconds(3), Duration.ofSeconds(10), 0,
                        Duration.ofSeconds(1), null)));
    }
}
