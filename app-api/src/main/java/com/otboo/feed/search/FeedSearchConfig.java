package com.otboo.feed.search;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.otboo.feed.search.elasticsearch.ElasticsearchFeedSearch;
import com.otboo.feed.search.elasticsearch.FeedDocumentLoader;
import com.otboo.feed.search.elasticsearch.FeedIndexEventListener;
import com.otboo.feed.search.elasticsearch.FeedIndexInitializer;
import com.otboo.feed.search.elasticsearch.FeedIndexManager;
import com.otboo.feed.search.elasticsearch.FeedIndexer;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * 검색 엔진 조립.
 * 검색 엔진이 둘 다 스캔되면 {@code FeedSearchPort} 주입이 모호해진다.
 * ES 관련 빈이 ES 를 안 쓰는 환경에서도 만들어짐 -> 피드를 쓸 때마다 접속 실패 로그가 쌓임.
 *
 * <h2>기본값이 MySQL 인 이유</h2>
 * 팀원 로컬이나 CI 에 ES 가 없어도 앱이 그대로 떠야 한다. ES 는 명시적으로 켠다
 * ({@code otboo.search.engine=elasticsearch}).
 */
@Configuration
@EnableConfigurationProperties(SearchProperties.class)
public class FeedSearchConfig {

    /**
     * MySQL 구현은 ES 를 쓸 때도 살려 둔다. 폴백 대상이면서, 안전장치용
     * {@code @Bean} 으로 두는 것은 {@code @Transactional} 프록시를 받기 위해서다.
     */
    @Bean
    public MysqlFeedSearch mysqlFeedSearch(NamedParameterJdbcTemplate jdbc) {
        return new MysqlFeedSearch(jdbc);
    }

    /**
     * 실제로 주입되는 검색. 설정에 따라 MySQL 이거나, ES(+폴백)다.
     *
     * <p>{@code @Primary} 가 필요** -> {@link MysqlFeedSearch}={@code FeedSearchPort}
     * 타입으로는 differentiate x.
     *
     * <p>엔진마다 {@link TimedFeedSearch} 로 따로 감싼다. 폴백 바깥 하나만 감싸면
     * 폴백된 MySQL 시간이 ES 로 집계돼 엔진 비교가 틀어진다.
     */
    @Bean
    @Primary
    public FeedSearchPort feedSearchPort(
            SearchProperties properties,
            MysqlFeedSearch mysqlFeedSearch,
            ObjectProvider<ElasticsearchFeedSearch> elasticsearch,
            MeterRegistry registry
    ) {
        FeedSearchPort mysql = new TimedFeedSearch("mysql", mysqlFeedSearch, registry);
        if (!properties.usesElasticsearch()) {
            return mysql;
        }
        FeedSearchPort primary = new TimedFeedSearch("elasticsearch", elasticsearch.getObject(), registry);
        return properties.fallbackToMysql()
                ? new FallbackFeedSearch(primary, mysql)
                : primary;
    }

    /**
     * ES용 빈들. 색인 파이프라인 전체.
     *
     * <p>{@code @Bean} 으로 선언해도 {@code @Transactional} · {@code @Async} 프록시는 그대로
     * 적용된다. 빈 등록 방식과 AOP 는 별개다.
     */
    @Configuration
    @ConditionalOnProperty(prefix = "otboo.search", name = "engine", havingValue = "elasticsearch")
    static class ElasticsearchConfig {

        @Bean
        FeedDocumentLoader feedDocumentLoader(NamedParameterJdbcTemplate jdbc) {
            return new FeedDocumentLoader(jdbc);
        }

        @Bean
        FeedIndexManager feedIndexManager(
                ElasticsearchClient client,
                FeedDocumentLoader documentLoader,
                SearchProperties properties
        ) {
            return new FeedIndexManager(client, documentLoader, properties);
        }

        @Bean
        FeedIndexer feedIndexer(
                ElasticsearchClient client,
                FeedDocumentLoader documentLoader,
                FeedIndexManager indexManager
        ) {
            return new FeedIndexer(client, documentLoader, indexManager);
        }

        @Bean
        FeedIndexEventListener feedIndexEventListener(FeedIndexer indexer) {
            return new FeedIndexEventListener(indexer);
        }

        @Bean
        ElasticsearchFeedSearch elasticsearchFeedSearch(
                ElasticsearchClient client,
                FeedIndexManager indexManager
        ) {
            return new ElasticsearchFeedSearch(client, indexManager);
        }

        @Bean
        FeedIndexInitializer feedIndexInitializer(
                FeedIndexManager indexManager,
                SearchProperties properties
        ) {
            return new FeedIndexInitializer(indexManager, properties);
        }
    }
}
