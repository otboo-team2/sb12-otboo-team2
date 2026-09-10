package com.otboo.common.http;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ExternalApiClientFactoryTest {

    @Test
    void explicitNeverDoesNotFollowRedirect() throws IOException {
        AtomicInteger finalRequestCount = new AtomicInteger();
        HttpServer server = redirectServer(finalRequestCount);
        server.start();

        try {
            ExternalApiClientFactory factory = factory();
            String redirectUri = "http://localhost:%d/redirect".formatted(server.getAddress().getPort());
            ExternalApiClient client = factory.create(
                    "redirect-test",
                    HttpClient.Redirect.NEVER,
                    builder -> { });

            int status = client.exchange("GET " + redirectUri,
                    restClient -> restClient.get()
                            .uri(redirectUri)
                            .exchange((request, response) -> response.getStatusCode().value()));

            assertThat(status).isEqualTo(302);
            assertThat(finalRequestCount).hasValue(0);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void existingFactoryCallUsesNormalRedirects() throws IOException {
        AtomicInteger finalRequestCount = new AtomicInteger();
        HttpServer server = redirectServer(finalRequestCount);
        server.start();

        try {
            ExternalApiClientFactory factory = factory();
            String redirectUri = "http://localhost:%d/redirect".formatted(server.getAddress().getPort());
            ExternalApiClient client = factory.create("redirect-test", builder -> { });

            int status = client.exchange("GET " + redirectUri,
                    restClient -> restClient.get()
                            .uri(redirectUri)
                            .exchange((request, response) -> response.getStatusCode().value()));

            assertThat(status).isEqualTo(200);
            assertThat(finalRequestCount).hasValue(1);
        } finally {
            server.stop(0);
        }
    }

    private ExternalApiClientFactory factory() {
        return new ExternalApiClientFactory(new ExternalApiProperties(null, null));
    }

    private HttpServer redirectServer(AtomicInteger finalRequestCount) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/redirect", exchange -> {
            exchange.getResponseHeaders().add("Location", "/final");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.createContext("/final", exchange -> {
            finalRequestCount.incrementAndGet();
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        return server;
    }
}
