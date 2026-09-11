package com.otboo.clothes.extraction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.otboo.clothes.exception.ClothesErrorCode;
import com.otboo.common.exception.BusinessException;
import com.otboo.common.http.ExternalApiClientFactory;
import com.otboo.common.http.ExternalApiProperties;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SafeRemoteResourceClientTest {

    private HttpServer server;
    private ProductUrlValidator urlValidator;
    private SafeRemoteResourceClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.start();

        urlValidator = mock(ProductUrlValidator.class);
        given(urlValidator.validate(any(URI.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        ExternalApiClientFactory factory = new ExternalApiClientFactory(
                new ExternalApiProperties(null, null));
        client = new SafeRemoteResourceClient(
                factory,
                urlValidator,
                properties(8, 8));
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void downloadsHtmlWithinConfiguredLimit() {
        server.createContext("/html", exchange -> respond(
                exchange,
                200,
                "text/html; charset=UTF-8",
                "ok".getBytes(StandardCharsets.UTF_8)));

        URI uri = uri("/html");
        RemoteResource result = client.getHtml(uri);

        assertThat(result.finalUri()).isEqualTo(uri);
        assertThat(result.contentType()).startsWith("text/html");
        assertThat(result.body()).isEqualTo("ok".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void validatesRedirectLocationBeforeFollowingIt() {
        AtomicInteger finalRequests = new AtomicInteger();
        server.createContext("/start", exchange -> redirect(exchange, "/final"));
        server.createContext("/final", exchange -> {
            finalRequests.incrementAndGet();
            respond(exchange, 200, "text/html", "done".getBytes(StandardCharsets.UTF_8));
        });

        URI start = uri("/start");
        URI finalUri = uri("/final");
        RemoteResource result = client.getHtml(start);

        assertThat(result.finalUri()).isEqualTo(finalUri);
        assertThat(finalRequests).hasValue(1);
        org.mockito.Mockito.verify(urlValidator).validate(finalUri);
    }

    @Test
    void rejectsTheFourthRedirect() {
        AtomicInteger requests = new AtomicInteger();
        server.createContext("/r0", exchange -> redirectAndCount(exchange, "/r1", requests));
        server.createContext("/r1", exchange -> redirectAndCount(exchange, "/r2", requests));
        server.createContext("/r2", exchange -> redirectAndCount(exchange, "/r3", requests));
        server.createContext("/r3", exchange -> redirectAndCount(exchange, "/r4", requests));
        server.createContext("/r4", exchange -> respond(
                exchange, 200, "text/html", "unexpected".getBytes(StandardCharsets.UTF_8)));

        assertThatThrownBy(() -> client.getHtml(uri("/r0")))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ClothesErrorCode.UNSAFE_PRODUCT_URL));
        assertThat(requests).hasValue(4);
    }

    @Test
    void rejectsUnsafeRedirectBeforeMakingNextRequest() {
        AtomicInteger requests = new AtomicInteger();
        server.createContext("/start", exchange -> {
            requests.incrementAndGet();
            redirect(exchange, "https://127.0.0.1/admin");
        });

        URI unsafeUri = URI.create("https://127.0.0.1/admin");
        given(urlValidator.validate(unsafeUri))
                .willThrow(new BusinessException(ClothesErrorCode.UNSAFE_PRODUCT_URL));

        assertThatThrownBy(() -> client.getHtml(uri("/start")))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ClothesErrorCode.UNSAFE_PRODUCT_URL));
        assertThat(requests).hasValue(1);
    }

    @Test
    void rejectsHtmlLargerThanConfiguredLimit() {
        server.createContext("/large-html", exchange -> respond(
                exchange,
                200,
                "text/html",
                "123456789".getBytes(StandardCharsets.UTF_8)));

        assertThatThrownBy(() -> client.getHtml(uri("/large-html")))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ClothesErrorCode.REMOTE_RESOURCE_TOO_LARGE));
    }

    @Test
    void rejectsImageLargerThanConfiguredLimit() {
        server.createContext("/large-image", exchange -> respond(
                exchange, 200, "image/png", new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9}));

        assertThatThrownBy(() -> client.getImage(uri("/large-image")))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ClothesErrorCode.INVALID_REMOTE_IMAGE));
    }

    @Test
    void rejectsHtmlResponseWhenImageWasRequested() {
        server.createContext("/not-image", exchange -> respond(
                exchange, 200, "text/html", "not an image".getBytes(StandardCharsets.UTF_8)));

        assertThatThrownBy(() -> client.getImage(uri("/not-image")))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ClothesErrorCode.INVALID_REMOTE_IMAGE));
    }

    private URI uri(String path) {
        return URI.create("http://localhost:%d%s".formatted(server.getAddress().getPort(), path));
    }

    private static ClothesExtractionProperties properties(int htmlLimit, int imageLimit) {
        return new ClothesExtractionProperties(
                "", "gemini-test", 3, htmlLimit, imageLimit, imageLimit * 2, 4, 15_000);
    }

    private static void redirect(HttpExchange exchange, String location) throws IOException {
        exchange.getResponseHeaders().add("Location", location);
        exchange.sendResponseHeaders(302, -1);
        exchange.close();
    }

    private static void redirectAndCount(
            HttpExchange exchange,
            String location,
            AtomicInteger requests
    ) throws IOException {
        requests.incrementAndGet();
        redirect(exchange, location);
    }

    private static void respond(
            HttpExchange exchange,
            int status,
            String contentType,
            byte[] body
    ) throws IOException {
        exchange.getResponseHeaders().add("Content-Type", contentType);
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }
}
