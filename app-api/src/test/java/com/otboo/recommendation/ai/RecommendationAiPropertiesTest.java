package com.otboo.recommendation.ai;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class RecommendationAiPropertiesTest {
    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withUserConfiguration(Config.class)
            .withPropertyValues("otboo.recommendation.ai.embedding-model=text-embedding-3-small");

    @Test
    void matchingDimensionsStarts() {
        context.withPropertyValues("otboo.recommendation.ai.embedding-dimensions=1536").run(c -> {
            assertThat(c).hasNotFailed();
            assertThat(c.getBean(RecommendationAiProperties.class).embeddingDimensions()).isEqualTo(1536);
        });
    }

    @Test
    void incompatibleDimensionsFailsAtStartup() {
        context.withPropertyValues("otboo.recommendation.ai.embedding-dimensions=768")
                .run(c -> assertThat(c).hasFailed());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(RecommendationAiProperties.class)
    static class Config { }
}
