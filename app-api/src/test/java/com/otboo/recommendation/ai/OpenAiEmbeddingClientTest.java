package com.otboo.recommendation.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.otboo.common.exception.BusinessException;
import com.otboo.common.exception.CommonErrorCode;
import com.otboo.common.http.ExternalApiClient;
import com.otboo.common.http.ExternalApiClientFactory;
import com.otboo.common.http.ExternalApiProperties;
import java.io.IOException;
import java.net.http.HttpClient;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class OpenAiEmbeddingClientTest {

    private static final int DIMENSIONS = 1536;
    private MockRestServiceServer server;
    private OpenAiEmbeddingClient client;

    @BeforeEach
    void setUp() {
        ExternalApiClientFactory factory = new ExternalApiClientFactory(
                new ExternalApiProperties(null, Map.of())) {
            @Override
            public ExternalApiClient create(String name, HttpClient.Redirect redirect,
                    Consumer<RestClient.Builder> customizer) {
                assertThat(name).isEqualTo("llm");
                assertThat(redirect).isEqualTo(HttpClient.Redirect.NEVER);
                return super.create(name, redirect, builder -> {
                    customizer.accept(builder);
                    server = MockRestServiceServer.bindTo(builder).build();
                });
            }
        };
        client = new OpenAiEmbeddingClient(
                factory,
                new RecommendationAiProperties(
                        "test-key", "gpt-5.6-luna", "https://api.openai.com/v1",
                        "text-embedding-3-small", DIMENSIONS),
                new ObjectMapper());
    }

    @Test
    void returns1536DimensionEmbedding() {
        expectSuccess(embeddingResponse(DIMENSIONS));

        var result = client.embed("셔츠 타입:TOP 속성:스타일=캐주얼");

        assertThat(result).hasSize(DIMENSIONS);
        assertThat(result.get(0)).isEqualTo(0.0f);
        server.verify();
    }

    @Test
    void rejectsBlankInputWithoutCallingApi() {
        assertThatThrownBy(() -> client.embed("  "))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_INPUT_VALUE));
        server.verify();
    }

    @Test
    void rejectsEmptyData() {
        expectSuccess("{\"data\":[]}");
        assertExternalFailure();
    }

    @Test
    void rejectsMissingEmbedding() {
        expectSuccess("{\"data\":[{\"index\":0}]} ");
        assertExternalFailure();
    }

    @Test
    void rejectsDimensionMismatch() {
        expectSuccess(embeddingResponse(DIMENSIONS - 1));
        assertExternalFailure();
    }

    @Test
    void rejectsMalformedResponse() {
        expectSuccess("not-json");
        assertExternalFailure();
    }

    @Test
    void translatesHttp4xx() {
        server.expect(requestTo("https://api.openai.com/v1/embeddings"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));
        assertExternalFailure();
    }

    @Test
    void translatesHttp5xx() {
        server.expect(requestTo("https://api.openai.com/v1/embeddings"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));
        assertExternalFailure();
    }

    @Test
    void translatesTimeoutOrNetworkFailure() {
        server.expect(requestTo("https://api.openai.com/v1/embeddings"))
                .andRespond(withException(new IOException("network failure")));

        assertThatThrownBy(() -> client.embed("의상 content"))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.EXTERNAL_API_TIMEOUT));
        server.verify();
    }

    private void expectSuccess(String response) {
        server.expect(requestTo("https://api.openai.com/v1/embeddings"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));
    }

    private void assertExternalFailure() {
        assertThatThrownBy(() -> client.embed("의상 content"))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.EXTERNAL_API_ERROR));
        server.verify();
    }

    private String embeddingResponse(int size) {
        StringBuilder values = new StringBuilder();
        for (int index = 0; index < size; index++) {
            if (index > 0) {
                values.append(',');
            }
            values.append(index / 1000.0);
        }
        return "{\"object\":\"list\",\"data\":[{\"object\":\"embedding\","
                + "\"index\":0,\"embedding\":[" + values + "]}]}";
    }
}
