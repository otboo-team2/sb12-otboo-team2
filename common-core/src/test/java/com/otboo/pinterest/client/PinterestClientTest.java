package com.otboo.pinterest.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.otboo.common.exception.BusinessException;
import com.otboo.common.http.ExternalApiClientFactory;
import com.otboo.common.http.ExternalApiProperties;
import com.otboo.pinterest.PinterestProperties;
import com.otboo.pinterest.exception.PinterestErrorCode;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 로컬 HTTP 서버로 실제 요청 모양(경로 · 쿼리 인코딩 · 헤더)과 응답 매핑을 확인한다.
 * {@code ExternalApiClient} 를 목으로 바꾸면 정작 틀리기 쉬운 URI 인코딩을 검증할 수 없다.
 */
class PinterestClientTest {

    private static final String PIN_PAGE = """
            {
              "items": [
                {
                  "id": "813744226420795884",
                  "board_id": "549755885175",
                  "link": "https://shop.example.com/item/1",
                  "title": "겨울 니트",
                  "description": "@otboo temp:5-8 sky:cloudy style:minimal gender:unisex",
                  "alt_text": null,
                  "created_at": "2026-09-01T03:04:05",
                  "media": {
                    "media_type": "image",
                    "images": {
                      "150x150": {"width": 150, "height": 150, "url": "https://i.pinimg.com/150x150/a.jpg"},
                      "600x": {"width": 600, "height": 900, "url": "https://i.pinimg.com/600x/a.jpg"}
                    }
                  },
                  "metrics": {"unexpected": "shape"}
                }
              ],
              "bookmark": "abc+/=="
            }
            """;

    private HttpServer server;
    private final AtomicReference<String> requestedUri = new AtomicReference<>();
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private final AtomicInteger requestCount = new AtomicInteger();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            requestCount.incrementAndGet();
            requestedUri.set(exchange.getRequestURI().getRawPath() + "?" + exchange.getRequestURI().getRawQuery());
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] body = PIN_PAGE.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    @DisplayName("보드 핀 목록을 토큰과 함께 요청하고 필요한 필드만 매핑한다 — 타임존 없는 created_at · 모르는 필드가 있어도 깨지지 않는다")
    void listsBoardPins() {
        var page = client("token-123").listBoardPins("549755885175", null);

        assertThat(requestedUri.get()).isEqualTo("/v5/boards/549755885175/pins?page_size=100");
        assertThat(authorization.get()).isEqualTo("Bearer token-123");

        assertThat(page.hasNext()).isTrue();
        PinterestPinResponse pin = page.items().get(0);
        assertThat(pin.id()).isEqualTo("813744226420795884");
        assertThat(pin.boardId()).isEqualTo("549755885175");
        assertThat(pin.link()).isEqualTo("https://shop.example.com/item/1");
        assertThat(pin.imageUrl()).contains("https://i.pinimg.com/600x/a.jpg");
    }

    @Test
    @DisplayName("bookmark 의 + / = 가 그대로 전달되도록 인코딩한다")
    void encodesBookmark() {
        client("token").listBoardPins("1", "abc+/==");

        assertThat(requestedUri.get()).isEqualTo("/v5/boards/1/pins?page_size=100&bookmark=abc%2B%2F%3D%3D");
    }

    @Test
    @DisplayName("검색어의 공백 · & · 한글이 쿼리를 깨지 않는다")
    void encodesSearchQuery() {
        client("token").searchPins("minimal & street 니트", null);

        assertThat(requestedUri.get())
                .isEqualTo("/v5/search/pins?query=minimal%20%26%20street%20%EB%8B%88%ED%8A%B8");
    }

    @Test
    @DisplayName("토큰이 없으면 Pinterest 를 부르지 않고 실패한다")
    void failsWithoutTokenBeforeCalling() {
        assertThatThrownBy(() -> client("").listBoardPins("1", null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(PinterestErrorCode.ACCESS_TOKEN_NOT_CONFIGURED);
        assertThat(requestCount).hasValue(0);
    }

    @Test
    @DisplayName("보드 ID 가 숫자가 아니면 경로에 넣지 않는다")
    void rejectsNonNumericBoardId() {
        assertThatThrownBy(() -> client("token").listBoardPins("../../user_account", null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(PinterestErrorCode.INVALID_BOARD_ID);
        assertThat(requestCount).hasValue(0);
    }

    @Test
    @DisplayName("빈 검색어는 부르지 않는다")
    void rejectsBlankQuery() {
        assertThatThrownBy(() -> client("token").searchPins("  ", null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(PinterestErrorCode.INVALID_SEARCH_QUERY);
        assertThat(requestCount).hasValue(0);
    }

    private PinterestClient client(String token) {
        String baseUrl = "http://localhost:%d".formatted(server.getAddress().getPort());
        PinterestProperties properties = new PinterestProperties(baseUrl, token, List.of(), null);
        ExternalApiClientFactory factory = new ExternalApiClientFactory(new ExternalApiProperties(null, null));
        return new PinterestClient(factory, properties);
    }
}
