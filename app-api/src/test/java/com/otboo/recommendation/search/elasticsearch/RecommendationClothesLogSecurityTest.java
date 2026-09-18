package com.otboo.recommendation.search.elasticsearch;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.read.ListAppender;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.ErrorResponse;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import co.elastic.clients.elasticsearch.core.DeleteRequest;
import com.otboo.clothes.ClothesService;
import com.otboo.clothes.dto.ClothesDto;
import com.otboo.clothes.entity.ClothesType;
import com.otboo.common.exception.BusinessException;
import com.otboo.common.exception.GlobalExceptionHandler;
import com.otboo.common.http.*;
import com.otboo.recommendation.ai.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.http.HttpClient;
import java.util.*;
import java.util.function.Consumer;
import org.junit.jupiter.api.*;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class RecommendationClothesLogSecurityTest {
    private static final String SECRETS = "SENSITIVE_EXTERNAL_BODY_MARKER FAKE_API_KEY_MARKER Authorization: Bearer FAKE_API_KEY_MARKER SENSITIVE_VECTOR_MARKER";
    private final UUID id = UUID.randomUUID();
    private final ClothesService clothes = mock(ClothesService.class);
    private final ElasticsearchClient es = mock(ElasticsearchClient.class);
    private final RecommendationClothesEmbeddingService embeddings = mock(RecommendationClothesEmbeddingService.class);
    private final RecommendationClothesIndexManager manager = mock(RecommendationClothesIndexManager.class);
    private final RecommendationClothesIndexer indexer = new RecommendationClothesIndexer(es, clothes, embeddings, manager);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private final List<Logger> loggers = List.of(
            (Logger) LoggerFactory.getLogger(RecommendationClothesIndexer.class),
            (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class),
            (Logger) LoggerFactory.getLogger(ExternalApiClient.class));
    private MockRestServiceServer server;
    private OpenAiEmbeddingClient client;

    @BeforeEach void setup() throws Exception {
        appender.start(); loggers.forEach(l -> l.addAppender(appender));
        when(manager.alias()).thenReturn("recommendation-clothes");
        when(clothes.findForRecommendation(id)).thenReturn(List.of(new ClothesDto(id, UUID.randomUUID(), "test", null, ClothesType.TOP, false, List.of())));
        when(embeddings.embed(any())).thenReturn(List.of(0.1f));
        var factory = new ExternalApiClientFactory(new ExternalApiProperties(null, Map.of())) {
            @Override public ExternalApiClient create(String name, HttpClient.Redirect redirect, Consumer<RestClient.Builder> customizer) {
                return super.create(name, redirect, b -> { customizer.accept(b); server = MockRestServiceServer.bindTo(b).build(); });
            }
        };
        client = new OpenAiEmbeddingClient(factory, new RecommendationAiProperties("FAKE_API_KEY_MARKER", "unused", "https://example.invalid/v1", "text-embedding-3-small", 1536), new ObjectMapper());
    }
    @AfterEach void close() { loggers.forEach(l -> l.detachAppender(appender)); appender.stop(); }

    @Test void http400IsSafeInIndexerAndGlobalHandler() {
        server.expect(requestTo("https://example.invalid/v1/embeddings"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON).body("{\"error\":\"" + SECRETS + "\"}"));
        BusinessException failure = catchThrowableOfType(() -> client.embed("private content"), BusinessException.class);
        when(embeddings.embed(any())).thenThrow(failure);
        assertThat(indexer.index(id)).isFalse();
        new GlobalExceptionHandler().handleBusinessException(failure);
        assertSafe();
        assertThat(logText()).contains(id.toString(), "COMMON_901", "400", "stage=embedding", "exception_type=BusinessException");
        server.verify();
    }

    @Test void networkFailureCauseIsSafe() {
        server.expect(requestTo("https://example.invalid/v1/embeddings")).andRespond(withException(new IOException(SECRETS)));
        BusinessException failure = catchThrowableOfType(() -> client.embed("private content"), BusinessException.class);
        when(embeddings.embed(any())).thenThrow(failure);
        assertThat(indexer.index(id)).isFalse();
        new GlobalExceptionHandler().handleBusinessException(failure);
        assertSafe(); assertThat(logText()).contains(id.toString(), "COMMON_902", "stage=embedding", "status=none");
        server.verify();
    }

    @Test void translatedFailureIsSafeEvenWhenAnotherCallerLogsThrowable() {
        server.expect(requestTo("https://example.invalid/v1/embeddings")).andRespond(withStatus(HttpStatus.BAD_REQUEST).body(SECRETS));
        BusinessException failure = catchThrowableOfType(() -> client.embed("test"), BusinessException.class);
        loggers.getFirst().error("external_failure", failure);
        assertSafe(); server.verify();
        assertThat(failure.getDetails()).containsEntry("status", "400").containsEntry("api", "llm");
    }

    @Test void elasticsearchUpsertFailureIsSafe() throws Exception {
        when(es.index(any(IndexRequest.class))).thenThrow(esFailure());
        assertThat(indexer.index(id)).isFalse(); assertSafe();
        assertThat(logText()).contains(id.toString(), "stage=es_upsert", "status=400", "COMMON_901");
    }
    @Test void elasticsearchDeleteFailureIsSafe() throws Exception {
        when(es.delete(any(DeleteRequest.class))).thenThrow(esFailure());
        assertThat(indexer.delete(id)).isFalse(); assertSafe();
        assertThat(logText()).contains(id.toString(), "stage=es_delete", "status=400", "COMMON_901");
    }
    @Test void cleanupFailureIsSafe() {
        when(clothes.findForRecommendation(id)).thenThrow(new IllegalStateException(SECRETS, new IOException(SECRETS)));
        assertThat(indexer.deleteIfMissing(id)).isEqualTo(RecommendationClothesIndexer.CleanupResult.FAILED);
        assertSafe(); assertThat(logText()).contains(id.toString());
    }
    @Test void elasticsearchFailureEscapingToGlobalHandlerIsSafe() {
        new GlobalExceptionHandler().handleUnexpectedException(esFailure());
        assertSafe();
    }
    private ElasticsearchException esFailure() {
        return new ElasticsearchException("index", ErrorResponse.of(r -> r.status(400).error(e -> e.type("mapper_parsing_exception").reason(SECRETS))));
    }
    private String logText() {
        return appender.list.stream().map(e -> e.getFormattedMessage() + (e.getThrowableProxy() == null ? "" : ThrowableProxyUtil.asString(e.getThrowableProxy()))).reduce("", String::concat);
    }
    private void assertSafe() {
        assertThat(logText()).doesNotContain("SENSITIVE_EXTERNAL_BODY_MARKER", "FAKE_API_KEY_MARKER", "SENSITIVE_VECTOR_MARKER", "Authorization: Bearer");
    }
}
