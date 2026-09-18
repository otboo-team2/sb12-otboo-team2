package com.otboo.recommendation.search.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import co.elastic.clients.elasticsearch.indices.ExistsAliasRequest;
import co.elastic.clients.elasticsearch.indices.UpdateAliasesRequest;
import co.elastic.clients.elasticsearch.indices.RefreshRequest;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.search.Hit;
import java.util.List;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;

/** 추천 의상 인덱스의 생성과 명시적 복구용 문서 순회를 담당한다. */
@Slf4j
@RequiredArgsConstructor
public class RecommendationClothesIndexManager {

    private static final String MAPPING_RESOURCE =
            "search/recommendation-clothes-index.json";

    private final ElasticsearchClient client;
    private final RecommendationClothesSearchProperties properties;

    public String indexName() {
        return alias();
    }

    public String alias() {
        return properties.indexName();
    }

    public String physicalIndexName() {
        return alias() + "-v1";
    }

    public boolean exists() throws IOException {
        return client.indices().exists(ExistsRequest.of(e -> e.index(physicalIndexName()))).value();
    }

    /** 인덱스가 없을 때만 명시한 매핑으로 생성한다. */
    public boolean createIndexIfMissing() throws IOException {
        if (client.indices().existsAlias(ExistsAliasRequest.of(a -> a.name(alias()))).value()) {
            return false;
        }
        if (!exists()) {
            try (Reader mapping = mappingReader()) {
                // 인덱스와 alias를 함께 생성해 중간 상태를 남기지 않는다.
                client.indices().create(CreateIndexRequest.of(request -> request
                        .index(physicalIndexName()).withJson(mapping).aliases(alias(), a -> a)));
                log.info("추천 의상 Elasticsearch 인덱스를 생성했다. index={}, alias={}", physicalIndexName(), alias());
                return true;
            } catch (ElasticsearchException e) {
                if (!"resource_already_exists_exception".equals(e.error().type()) || !exists()) {
                    throw e;
                }
            }
        }
        // 이전 버전에서 물리 인덱스만 생성했거나 다른 인스턴스가 먼저 생성한 경우.
        client.indices().updateAliases(UpdateAliasesRequest.of(r -> r.actions(a -> a
                .add(add -> add.index(physicalIndexName()).alias(alias())))));
        return false;
    }

    public void refresh() throws IOException {
        var response = client.indices().refresh(RefreshRequest.of(r -> r.index(alias())));
        if (response.shards().failed().intValue() > 0) {
            throw new IOException("추천 의상 인덱스 refresh 일부 실패");
        }
    }

    /** 복구용 ID만 순회한다. content/embedding은 가져오지 않는다. */
    public List<Hit<Void>> documentIdsAfter(List<FieldValue> after, int size) throws IOException {
        var response = client.search(SearchRequest.of(r -> r.index(alias())
                .size(size).source(s -> s.fetch(false)).allowPartialSearchResults(false)
                .sort(s -> s.field(f -> f.field("clothesId").order(SortOrder.Asc)))
                .searchAfter(after)), Void.class);
        if (response.timedOut() || response.shards().failed().intValue() > 0) {
            throw new IOException("추천 의상 복구 문서 조회가 완료되지 않음");
        }
        return response.hits().hits();
    }

    private Reader mappingReader() {
        try {
            InputStream stream = new ClassPathResource(MAPPING_RESOURCE).getInputStream();
            return new InputStreamReader(stream, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("추천 의상 매핑 파일을 읽지 못했다: " + MAPPING_RESOURCE, e);
        }
    }
}
