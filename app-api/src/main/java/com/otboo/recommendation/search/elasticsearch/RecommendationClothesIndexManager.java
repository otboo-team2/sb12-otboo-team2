package com.otboo.recommendation.search.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import co.elastic.clients.elasticsearch.indices.ExistsAliasRequest;
import co.elastic.clients.elasticsearch.indices.GetAliasRequest;
import co.elastic.clients.elasticsearch.indices.UpdateAliasesRequest;
import co.elastic.clients.elasticsearch.indices.RefreshRequest;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.search.Hit;
import java.util.List;
import java.util.Set;
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
        return alias() + "-v2";
    }

    public boolean exists() throws IOException {
        return client.indices().exists(ExistsRequest.of(e -> e.index(physicalIndexName()))).value();
    }

    /** 인덱스가 없을 때만 명시한 매핑으로 생성한다. */
    public synchronized boolean createIndexIfMissing() throws IOException {
        boolean aliasExists = client.indices()
                .existsAlias(ExistsAliasRequest.of(a -> a.name(alias()))).value();
        Set<String> aliasedIndices = aliasExists
                ? client.indices().getAlias(GetAliasRequest.of(a -> a.name(alias()))).result().keySet()
                : Set.of();
        if (aliasedIndices.contains(physicalIndexName())) {
            return false;
        }

        boolean created = createPhysicalIndexIfMissing(!aliasExists);
        if (!aliasExists) {
            if (!created) {
                attachAlias();
            }
            return created;
        }

        // 기존 alias는 복사와 원자적 전환이 끝날 때까지 계속 서비스한다.
        var reindex = client.reindex(request -> request
                .source(source -> source.index(alias()))
                .dest(destination -> destination.index(physicalIndexName()))
                .refresh(true)
                .waitForCompletion(true));
        if (Boolean.TRUE.equals(reindex.timedOut()) || !reindex.failures().isEmpty()) {
            throw new IOException("추천 의상 인덱스 버전 복사 실패");
        }
        UpdateAliasesRequest.Builder aliases = new UpdateAliasesRequest.Builder();
        aliasedIndices.forEach(index -> aliases.actions(action -> action
                .remove(remove -> remove.index(index).alias(alias()))));
        aliases.actions(action -> action.add(add -> add.index(physicalIndexName()).alias(alias())));
        client.indices().updateAliases(aliases.build());
        log.info("추천 의상 Elasticsearch alias를 새 버전으로 전환했다. index={}, alias={}",
                physicalIndexName(), alias());
        return true;
    }

    private boolean createPhysicalIndexIfMissing(boolean attachAlias) throws IOException {
        if (exists()) {
            return false;
        }
        try (Reader mapping = mappingReader()) {
            CreateIndexRequest.Builder request = new CreateIndexRequest.Builder()
                    .index(physicalIndexName()).withJson(mapping);
            if (attachAlias) {
                request.aliases(alias(), alias -> alias);
            }
            client.indices().create(request.build());
            log.info("추천 의상 Elasticsearch 인덱스를 생성했다. index={}", physicalIndexName());
            return true;
        } catch (ElasticsearchException e) {
            if (!"resource_already_exists_exception".equals(e.error().type()) || !exists()) {
                throw e;
            }
            return false;
        }
    }

    private void attachAlias() throws IOException {
        client.indices().updateAliases(UpdateAliasesRequest.of(r -> r.actions(a -> a
                .add(add -> add.index(physicalIndexName()).alias(alias())))));
    }

    public void refresh() throws IOException {
        var response = client.indices().refresh(RefreshRequest.of(r -> r.index(alias())));
        if (response.shards().failed().intValue() > 0) {
            throw new IOException("추천 의상 인덱스 refresh 일부 실패");
        }
    }

    /** 복구용 ID만 순회한다. content/embedding은 가져오지 않는다. */
    public List<Hit<Void>> documentIdsAfter(List<FieldValue> after, int size) throws IOException {
        var response = client.search(SearchRequest.of(r -> {
            r.index(alias()).size(size).source(s -> s.fetch(false)).allowPartialSearchResults(false)
                    .sort(s -> s.field(f -> f.field("clothesId").order(SortOrder.Asc)));
            if (after != null && !after.isEmpty()) {
                r.searchAfter(after);
            }
            return r;
        }), Void.class);
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
