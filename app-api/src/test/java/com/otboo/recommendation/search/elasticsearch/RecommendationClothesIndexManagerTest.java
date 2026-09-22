package com.otboo.recommendation.search.elasticsearch;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.ErrorResponse;
import co.elastic.clients.elasticsearch.indices.*;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.endpoints.BooleanResponse;
import java.io.IOException;
import java.io.StringWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

class RecommendationClothesIndexManagerTest {
    private final ElasticsearchClient client = mock(ElasticsearchClient.class);
    private final ElasticsearchIndicesClient indices = mock(ElasticsearchIndicesClient.class);
    private final RecommendationClothesIndexManager manager = new RecommendationClothesIndexManager(
            client, new RecommendationClothesSearchProperties(true, null));

    @BeforeEach
    void setup() throws IOException {
        when(client.indices()).thenReturn(indices);
        when(indices.existsAlias(any(ExistsAliasRequest.class))).thenReturn(new BooleanResponse(false));
        when(indices.exists(any(ExistsRequest.class))).thenReturn(new BooleanResponse(false));
    }

    @Test
    void createsVersionedIndexAndAliasAtomicallyWithValidMapping() throws IOException {
        assertThat(manager.createIndexIfMissing()).isTrue();
        var captor = ArgumentCaptor.forClass(CreateIndexRequest.class);
        verify(indices).create(captor.capture());
        var request = captor.getValue();
        assertThat(manager.indexName()).isEqualTo("recommendation-clothes");
        assertThat(request.index()).isEqualTo("recommendation-clothes-v1");
        assertThat(request.aliases()).containsKey(manager.alias());
        assertThat(request.mappings().dynamic().jsonValue()).isEqualTo("strict");
        var fields = request.mappings().properties();
        assertThat(fields).containsOnlyKeys("clothesId", "ownerId", "type", "content", "embedding");
        for (String name : new String[]{"clothesId", "ownerId", "type"}) {
            assertThat(fields.get(name).isKeyword()).isTrue();
        }
        assertThat(fields.get("content").text().analyzer()).isEqualTo("korean");
        assertThat(fields.get("content").text().fields()).isEmpty();
        assertThat(fields.get("embedding").denseVector().dims()).isEqualTo(1536);
        assertThat(fields.get("embedding").denseVector().index()).isTrue();
        assertThat(fields.get("embedding").denseVector().similarity().jsonValue()).isEqualTo("cosine");
        assertThat(request.settings().analysis().analyzer()).containsOnlyKeys("korean");
        assertThat(request.settings().analysis().tokenizer()).containsOnlyKeys("korean_tokenizer");
        verify(indices, never()).updateAliases(any(UpdateAliasesRequest.class));
    }

    @Test
    void existingAliasIsNotReassigned() throws IOException {
        when(indices.existsAlias(any(ExistsAliasRequest.class))).thenReturn(new BooleanResponse(true));
        assertThat(manager.createIndexIfMissing()).isFalse();
        verify(indices, never()).create(any(CreateIndexRequest.class));
        verify(indices, never()).updateAliases(any(UpdateAliasesRequest.class));
    }

    @Test
    void attachesAliasToPreviouslyCreatedPhysicalIndex() throws IOException {
        when(indices.exists(any(ExistsRequest.class))).thenReturn(new BooleanResponse(true));
        assertThat(manager.createIndexIfMissing()).isFalse();
        verify(indices, never()).create(any(CreateIndexRequest.class));
        assertAliasAttachment();
    }

    @Test
    void concurrentCreationIsAcceptedOnlyAfterExistenceRecheck() throws IOException {
        when(indices.exists(any(ExistsRequest.class)))
                .thenReturn(new BooleanResponse(false), new BooleanResponse(true));
        when(indices.create(any(CreateIndexRequest.class))).thenThrow(failure("resource_already_exists_exception"));
        assertThat(manager.createIndexIfMissing()).isFalse();
        verify(indices, times(2)).exists(any(ExistsRequest.class));
        assertAliasAttachment();
    }

    @Test
    void missingIndexAfterConflictPropagatesOriginalException() throws IOException {
        var error = failure("resource_already_exists_exception");
        when(indices.create(any(CreateIndexRequest.class))).thenThrow(error);
        assertThatThrownBy(manager::createIndexIfMissing).isSameAs(error);
        verify(indices, never()).updateAliases(any(UpdateAliasesRequest.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"mapper_parsing_exception", "illegal_argument_exception", "security_exception"})
    void realElasticsearchErrorsAreNotSwallowed(String type) throws IOException {
        var error = failure(type);
        when(indices.create(any(CreateIndexRequest.class))).thenThrow(error);
        assertThatThrownBy(manager::createIndexIfMissing).isSameAs(error);
        verify(indices, times(1)).exists(any(ExistsRequest.class));
        verify(indices, never()).updateAliases(any(UpdateAliasesRequest.class));
    }

    @Test
    void connectionFailurePropagates() throws IOException {
        var error = new IOException("connection unavailable");
        when(indices.create(any(CreateIndexRequest.class))).thenThrow(error);
        assertThatThrownBy(manager::createIndexIfMissing).isSameAs(error);
    }

    @Test
    void aliasFailurePropagates() throws IOException {
        when(indices.exists(any(ExistsRequest.class))).thenReturn(new BooleanResponse(true));
        var error = failure("invalid_alias_name_exception");
        when(indices.updateAliases(any(UpdateAliasesRequest.class))).thenThrow(error);
        assertThatThrownBy(manager::createIndexIfMissing).isSameAs(error);
    }

    @Test
    void recoveryScanUsesAliasCursorAndDoesNotFetchVectors() throws IOException {
        var after = java.util.List.of(co.elastic.clients.elasticsearch._types.FieldValue.of("previous"));
        var response = co.elastic.clients.elasticsearch.core.SearchResponse.of(r -> r
                .took(1).timedOut(false).shards(s -> s.total(1).successful(1).failed(0))
                .hits(h -> h.hits(java.util.List.of())));
        when(client.search(any(co.elastic.clients.elasticsearch.core.SearchRequest.class), eq(Void.class)))
                .thenReturn((co.elastic.clients.elasticsearch.core.SearchResponse) response);
        assertThat(manager.documentIdsAfter(after, 100)).isEmpty();
        var captor = ArgumentCaptor.forClass(co.elastic.clients.elasticsearch.core.SearchRequest.class);
        verify(client).search(captor.capture(), eq(Void.class));
        var request = captor.getValue();
        assertThat(request.index()).containsExactly("recommendation-clothes");
        assertThat(request.source().fetch()).isFalse();
        assertThat(request.allowPartialSearchResults()).isFalse();
        assertThat(request.searchAfter().getFirst().stringValue()).isEqualTo("previous");
        assertThat(toJson(request)).contains("\"search_after\":[\"previous\"]");
        assertThat(request.sort().getFirst().field().field()).isEqualTo("clothesId");
        assertThat(request.size()).isEqualTo(100);
    }

    @ParameterizedTest
    @NullAndEmptySource
    void firstRecoveryPageOmitsSearchAfter(java.util.List<co.elastic.clients.elasticsearch._types.FieldValue> after)
            throws IOException {
        var response = co.elastic.clients.elasticsearch.core.SearchResponse.of(r -> r
                .took(1).timedOut(false).shards(s -> s.total(1).successful(1).failed(0))
                .hits(h -> h.hits(java.util.List.of())));
        when(client.search(any(co.elastic.clients.elasticsearch.core.SearchRequest.class), eq(Void.class)))
                .thenReturn((co.elastic.clients.elasticsearch.core.SearchResponse) response);

        manager.documentIdsAfter(after, 100);

        var captor = ArgumentCaptor.forClass(co.elastic.clients.elasticsearch.core.SearchRequest.class);
        verify(client).search(captor.capture(), eq(Void.class));
        assertThat(toJson(captor.getValue())).doesNotContain("search_after");
    }

    @Test
    void timedOutRecoveryScanIsNotReportedAsSuccessful() throws IOException {
        co.elastic.clients.elasticsearch.core.SearchResponse<Void> response =
                co.elastic.clients.elasticsearch.core.SearchResponse.of(r -> r
                        .took(1).timedOut(true).shards(s -> s.total(1).successful(1).failed(0))
                        .hits(h -> h.hits(java.util.List.of())));
        when(client.search(any(co.elastic.clients.elasticsearch.core.SearchRequest.class), eq(Void.class)))
                .thenReturn(response);
        assertThatThrownBy(() -> manager.documentIdsAfter(java.util.List.of(), 100))
                .isInstanceOf(IOException.class);
    }

    @Test
    void refreshShardFailureIsPropagated() throws IOException {
        when(indices.refresh(any(RefreshRequest.class))).thenReturn(RefreshResponse.of(r -> r
                .shards(s -> s.total(1).successful(0).failed(1))));
        assertThatThrownBy(manager::refresh).isInstanceOf(IOException.class);
    }

    private void assertAliasAttachment() throws IOException {
        var captor = ArgumentCaptor.forClass(UpdateAliasesRequest.class);
        verify(indices).updateAliases(captor.capture());
        var add = captor.getValue().actions().getFirst().add();
        assertThat(add.index()).isEqualTo("recommendation-clothes-v1");
        assertThat(add.alias()).isEqualTo("recommendation-clothes");
    }

    private ElasticsearchException failure(String type) {
        return new ElasticsearchException("es.indices.create", ErrorResponse.of(r -> r.status(400)
                .error(e -> e.type(type).reason("test failure"))));
    }

    private String toJson(co.elastic.clients.elasticsearch.core.SearchRequest request) {
        var mapper = new JacksonJsonpMapper();
        var output = new StringWriter();
        var generator = mapper.jsonProvider().createGenerator(output);
        request.serialize(generator, mapper);
        generator.close();
        return output.toString();
    }
}
