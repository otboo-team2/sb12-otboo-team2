package com.otboo.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.otboo.clothes.extraction.CImageSelectionProperties;
import com.otboo.clothes.extraction.ClothesExtractionConfig;
import com.otboo.clothes.extraction.ClothesExtractionProperties;
import com.otboo.common.config.ExternalApiConfig;
import com.otboo.common.http.ApiSettings;
import com.otboo.common.http.ExternalApiProperties;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

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
            // 이 runner는 DB18 모델을 실제로 띄우지 않는 기존 바인딩 테스트용이다.
            // C 기본값과 별개로, 모델이 필요 없는 테스트는 B0를 명시한다.
            .withPropertyValues("otboo.clothes.extraction.c-selector.mode=B0")
            .withUserConfiguration(ExternalApiConfig.class, ClothesExtractionConfig.class);

    private final ApplicationContextRunner cSelectorPropertiesRunner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(CSelectorPropertiesOnlyConfiguration.class);

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
            assertThat(properties.forApi("clothes-gemini").readTimeout())
                    .isEqualTo(Duration.ofSeconds(60));
            assertThat(properties.forApi("clothes-gemini").maxRetries()).isZero();
            assertThat(properties.forApi("clothes-gemini").dailyLimit()).isEqualTo(50L);
        });
    }

    @Test
    @DisplayName("의상 추출 제한과 Gemini 설정이 바인딩된다")
    void 의상_추출_설정이_바인딩된다() {
        runner.run(context -> {
            ClothesExtractionProperties properties =
                    context.getBean(ClothesExtractionProperties.class);

            assertThat(properties.geminiModel()).isEqualTo("gemini-3.5-flash-lite");
            assertThat(properties.maxRedirects()).isEqualTo(3);
            assertThat(properties.maxHtmlBytes()).isEqualTo(2 * 1024 * 1024);
            assertThat(properties.maxImageBytes()).isEqualTo(10 * 1024 * 1024);
            assertThat(properties.maxTotalImageBytes()).isEqualTo(25 * 1024 * 1024);
            assertThat(properties.maxDetailImages()).isEqualTo(6);
            assertThat(properties.maxPageTextChars()).isEqualTo(15_000);
            assertThat(properties.maxDiscoveredImageCandidates()).isEqualTo(200);
        });
    }

    @Test
    @DisplayName("C selector 기본값은 C 이고 분석 자원은 제한되어 있다")
    void cSelectorDefaultsToCAndOneConcurrentAnalysis() {
        cSelectorPropertiesRunner.run(context -> {
            CImageSelectionProperties properties =
                    context.getBean(CImageSelectionProperties.class);

            assertThat(properties.mode()).isEqualTo(CImageSelectionProperties.Mode.C);
            assertThat(properties.modelPath()).isNull();
            assertThat(properties.modelSha256())
                    .isEqualTo("AD4952EAA2E383DFC448858ED492D51ED159AC83DAEFF160FEE94D3D9E3F2D7D");
            assertThat(properties.maxSelectedImages()).isEqualTo(8);
            assertThat(properties.maxScanBytes()).isEqualTo(100L * 1024 * 1024);
            assertThat(properties.maxDecodedPixels()).isEqualTo(200_000_000L);
            assertThat(properties.maxConcurrentAnalyses()).isEqualTo(1);
            assertThat(properties.acquireTimeout()).isEqualTo(Duration.ofMillis(250));
            assertThat(properties.analysisTimeout()).isEqualTo(Duration.ofSeconds(45));
        });
    }

    @Test
    @DisplayName("B0는 명시적으로 선택할 수 있다")
    void b0CanBeSelectedExplicitly() {
        runner.withPropertyValues("otboo.clothes.extraction.c-selector.mode=B0")
                .run(context -> assertThat(context.getBean(CImageSelectionProperties.class).mode())
                        .isEqualTo(CImageSelectionProperties.Mode.B0));
    }

    @Test
    @DisplayName("설정에 없는 API 도 기본값으로 뜬다 — 블록을 빠뜨려도 앱이 죽지 않는다")
    void 설정없는_api는_기본값() {
        withProperties(properties -> assertThat(properties.forApi("아직-안-붙인-api"))
                .isEqualTo(new ApiSettings(Duration.ofSeconds(3), Duration.ofSeconds(10), 0,
                        Duration.ofSeconds(1), null)));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(CImageSelectionProperties.class)
    static class CSelectorPropertiesOnlyConfiguration {
    }
}
