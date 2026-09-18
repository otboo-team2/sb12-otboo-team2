package com.otboo.recommendation.search.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
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
}
