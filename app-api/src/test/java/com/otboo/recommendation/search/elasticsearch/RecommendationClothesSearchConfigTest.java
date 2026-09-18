package com.otboo.recommendation.search.elasticsearch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class RecommendationClothesSearchConfigTest {
    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withUserConfiguration(RecommendationClothesSearchConfig.class);

    @Test
    void disabledSearchDoesNotRequireElasticsearchClient() {
        context.withPropertyValues("otboo.recommendation.search.enabled=false").run(c -> {
            assertThat(c).hasNotFailed().doesNotHaveBean(RecommendationClothesIndexManager.class);
        });
    }

    @Test
    void enabledSearchUsesConfiguredAliasWithoutCallingElasticsearchAtStartup() {
        var client = mock(ElasticsearchClient.class);
        context.withBean(ElasticsearchClient.class, () -> client)
                .withBean(com.otboo.clothes.ClothesService.class, () -> mock(com.otboo.clothes.ClothesService.class))
                .withBean(com.otboo.clothes.repository.ClothesRepository.class, () -> mock(com.otboo.clothes.repository.ClothesRepository.class))
                .withBean(com.otboo.recommendation.ai.RecommendationClothesEmbeddingService.class,
                        () -> mock(com.otboo.recommendation.ai.RecommendationClothesEmbeddingService.class))
                .withPropertyValues("otboo.recommendation.search.enabled=true",
                        "otboo.recommendation.search.index-name=custom-clothes")
                .run(c -> {
                    assertThat(c).hasNotFailed();
                    var manager = c.getBean(RecommendationClothesIndexManager.class);
                    assertThat(manager.alias()).isEqualTo("custom-clothes");
                    assertThat(manager.physicalIndexName()).isEqualTo("custom-clothes-v1");
                    verifyNoInteractions(client);
                });
    }
}
