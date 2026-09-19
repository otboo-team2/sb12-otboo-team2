package com.otboo.recommendation.search.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.otboo.clothes.ClothesService;
import com.otboo.recommendation.ai.RecommendationClothesEmbeddingService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 추천 검색 인프라는 명시적으로 켠 환경에서만 등록한다. */
@Configuration
@EnableConfigurationProperties(RecommendationClothesSearchProperties.class)
@ConditionalOnProperty(prefix = "otboo.recommendation.search", name = "enabled",
        havingValue = "true")
public class RecommendationClothesSearchConfig {

    @Bean
    RecommendationClothesIndexManager recommendationClothesIndexManager(
            ElasticsearchClient client,
            RecommendationClothesSearchProperties properties
    ) {
        return new RecommendationClothesIndexManager(client, properties);
    }

    @Bean
    RecommendationClothesVectorSearch recommendationClothesVectorSearch(
            ElasticsearchClient client,
            RecommendationClothesIndexManager indexManager
    ) {
        return new RecommendationClothesVectorSearch(client, indexManager);
    }

    @Bean
    RecommendationClothesIndexer recommendationClothesIndexer(
            ElasticsearchClient client,
            ClothesService clothesService,
            RecommendationClothesEmbeddingService embeddingService,
            RecommendationClothesIndexManager indexManager
    ) {
        return new RecommendationClothesIndexer(
                client, clothesService, embeddingService, indexManager);
    }

    @Bean
    RecommendationClothesIndexEventListener recommendationClothesIndexEventListener(
            RecommendationClothesIndexer indexer
    ) {
        return new RecommendationClothesIndexEventListener(indexer);
    }

    @Bean
    RecommendationClothesReindexService recommendationClothesReindexService(
            com.otboo.clothes.repository.ClothesRepository clothesRepository,
            RecommendationClothesIndexer indexer,
            RecommendationClothesIndexManager indexManager
    ) {
        return new RecommendationClothesReindexService(
                clothesRepository, indexer, indexManager);
    }
}
