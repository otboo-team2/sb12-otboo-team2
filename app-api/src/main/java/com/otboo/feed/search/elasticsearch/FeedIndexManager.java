package com.otboo.feed.search.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import com.otboo.feed.search.SearchProperties;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;

/**
 * 피드 인덱스의 생성 · 전체 재색인을 맡는다.
 *
 * <h2>왜 별칭(alias)을 두는가</h2>
 * 애플리케이션은 {@code feeds}에만 읽고 쓴다. 실제 인덱스는 {@code feeds-v1},
 * {@code feeds-v2} … 다. 매핑(분석기·필드 타입)은 수정불가.
 * 형태소 사전을 손보거나 필드를 추가하려면 인덱스를 새로 만들어야 하는데,
 * 별칭이 없으면 그 사이 검색이 멈춘다.
 *
 * <p>{@link #reindexAll()} 은 새 인덱스에 전부 넣은 <b>뒤에</b> 별칭을 원자적으로 옮긴다.
 * 스왑 전까지 검색은 계속 옛 인덱스를 보므로 다운타임이 없고, 중간에 실패하면
 * 별칭이 그대로라 <b>망가진 인덱스가 노출되지 않는다.</b>
 */
@Slf4j
public class FeedIndexManager {

    private static final String MAPPING_RESOURCE = "search/feed-index.json";

    private final ElasticsearchClient client;
    private final FeedDocumentLoader documentLoader;
    private final SearchProperties properties;

    public FeedIndexManager(
            ElasticsearchClient client,
            FeedDocumentLoader documentLoader,
            SearchProperties properties
    ) {
        this.client = client;
        this.documentLoader = documentLoader;
        this.properties = properties;
    }

    /** 애플리케이션이 읽고 쓰는 이름. 물리 인덱스가 아니라 별칭이다. */
    public String alias() {
        return properties.indexName();
    }

    /**
     * 별칭이 없으면 {@code <alias>-v1} 을 만들고 별칭을 붙인다. 이미 있으면 아무것도 하지 않는다.
     *
     * @return 새로 만들었으면 true
     */
    public boolean createIndexIfMissing() throws IOException {
        if (aliasExists()) {
            return false;
        }
        String index = alias() + "-v1";
        createIndex(index);
        attachAlias(index, null);
        log.info("피드 검색 인덱스를 생성했다. index={}, alias={}", index, alias());
        return true;
    }

    /** 별칭이 가리키는 문서 수. 색인이 비어 있는지 판단할 때 쓴다. */
    public long count() throws IOException {
        return client.count(c -> c.index(alias())).count();
    }

    /**
     * 전체 재색인. 새 인덱스를 만들어 MySQL 전량을 넣고 별칭을 옮긴 뒤 옛 인덱스를 지운다.
     *
     * @return 색인한 문서 수
     */
    public long reindexAll() throws IOException {
        String previous = currentIndex();
        String next = nextIndexName(previous);

        createIndex(next);
        long indexed = 0;
        try {
            UUID afterId = null;
            while (true) {
                List<FeedDocument> batch = documentLoader.loadBatch(afterId, properties.bulkSize());
                if (batch.isEmpty()) {
                    break;
                }
                bulkIndex(next, batch);
                indexed += batch.size();
                afterId = UUID.fromString(batch.getLast().id());
            }
            // 별칭을 옮기기 전에 새 인덱스를 검색 가능한 상태로 만든다.
            // 이게 없으면 스왑 직후 몇 초 동안 "검색은 되는데 0건" 이 나온다.
            client.indices().refresh(r -> r.index(next));
            attachAlias(next, previous);
        } catch (RuntimeException | IOException e) {
            // 별칭은 아직 옛 인덱스를 가리킨다. 반쯤 채운 인덱스만 치우면 원상태다.
            deleteQuietly(next);
            throw e;
        }

        if (previous != null) {
            deleteQuietly(previous);
        }
        log.info("피드 전체 재색인 완료. index={}, documents={}", next, indexed);
        return indexed;
    }

    /** 지정한 인덱스에 문서를 한 번에 넣는다. 실패한 항목이 있으면 예외로 알린다. */
    public void bulkIndex(String index, List<FeedDocument> documents) throws IOException {
        if (documents.isEmpty()) {
            return;
        }
        BulkRequest.Builder request = new BulkRequest.Builder();
        for (FeedDocument document : documents) {
            request.operations(op -> op.index(idx -> idx
                    .index(index)
                    .id(document.id())
                    .document(document)));
        }
        BulkResponse response = client.bulk(request.build());
        if (response.errors()) {
            String reasons = response.items().stream()
                    .map(BulkResponseItem::error)
                    .filter(java.util.Objects::nonNull)
                    .map(error -> error.type() + ": " + error.reason())
                    .distinct()
                    .limit(3)
                    .reduce((a, b) -> a + " | " + b)
                    .orElse("unknown");
            throw new IOException("피드 색인 일부가 실패했다: " + reasons);
        }
    }

    public boolean aliasExists() throws IOException {
        return client.indices().existsAlias(a -> a.name(alias())).value();
    }

    /** 별칭이 지금 가리키는 물리 인덱스. 없으면 {@code null}. */
    private String currentIndex() throws IOException {
        if (!aliasExists()) {
            return null;
        }
        return client.indices().getAlias(a -> a.name(alias()))
                .result().keySet().stream().findFirst().orElse(null);
    }

    private String nextIndexName(String previous) {
        if (previous == null) {
            return alias() + "-v1";
        }
        String suffix = previous.substring(previous.lastIndexOf("-v") + 2);
        try {
            return alias() + "-v" + (Integer.parseInt(suffix) + 1);
        } catch (NumberFormatException e) {
            // 사람이 손으로 만든 인덱스에 별칭을 붙여 놨을 수 있다. 이름 규칙을 강요하지 않는다.
            return alias() + "-v" + System.currentTimeMillis();
        }
    }

    private void createIndex(String index) throws IOException {
        try (Reader mapping = mappingReader()) {
            client.indices().create(c -> c.index(index).withJson(mapping));
        }
    }

    /** 별칭을 {@code next} 로 옮긴다. 추가와 제거가 한 요청 안에서 원자적으로 일어난다. */
    private void attachAlias(String next, String previous) throws IOException {
        client.indices().updateAliases(builder -> {
            builder.actions(a -> a.add(add -> add.index(next).alias(alias())));
            if (previous != null && !previous.equals(next)) {
                builder.actions(a -> a.remove(rm -> rm.index(previous).alias(alias())));
            }
            return builder;
        });
    }

    /** 정리 실패가 원래 작업의 결과를 뒤집으면 안 된다. 남은 인덱스는 사람이 지우면 된다. */
    private void deleteQuietly(String index) {
        try {
            client.indices().delete(d -> d.index(index).ignoreUnavailable(true));
        } catch (IOException | RuntimeException e) {
            log.warn("인덱스 정리에 실패했다. 수동으로 지워야 한다. index={}", index, e);
        }
    }

    private Reader mappingReader() {
        try {
            InputStream stream = new ClassPathResource(MAPPING_RESOURCE).getInputStream();
            return new InputStreamReader(stream, StandardCharsets.UTF_8);
        } catch (IOException e) {
            // 리소스가 jar 에 안 들어간 것이라 재시도해도 소용없다. 기동 시 바로 드러나야 한다.
            throw new UncheckedIOException("색인 매핑 파일을 읽지 못했다: " + MAPPING_RESOURCE, e);
        }
    }

    /** 테스트에서 색인 직후 검색하려면 refresh 가 필요하다(기본 1초 주기). */
    public void refresh() throws IOException {
        client.indices().refresh(r -> r.index(alias()));
    }

    /** 별칭이 가리키는 인덱스 이름들. 운영 확인용. */
    public Set<String> indices() throws IOException {
        if (!aliasExists()) {
            return Set.of();
        }
        return client.indices().getAlias(a -> a.name(alias())).result().keySet();
    }
}
