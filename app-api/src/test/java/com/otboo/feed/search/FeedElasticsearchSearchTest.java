package com.otboo.feed.search;

import static org.assertj.core.api.Assertions.assertThat;

import com.otboo.common.pagination.CursorRequest;
import com.otboo.common.pagination.SortDirection;
import com.otboo.common.test.IntegrationTestSupport;
import com.otboo.feed.dto.FeedSearchCondition;
import com.otboo.feed.search.elasticsearch.FeedIndexManager;
import com.otboo.user.entity.User;
import com.otboo.user.repository.UserRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Elasticsearch 검색 통합 테스트.
 *
 * <h2>왜 실제 컨테이너를 띄우나</h2>
 * 검증하려는 것이 한국어 분석 결과와 커서 페이지네이션의 경계이다.
 * 둘 다 ES 가 실제로 어떻게 토큰을 쪼개고 {@code search_after} 를 해석하는지에 달려 있어,
 * 클라이언트를 목으로 바꾸면 <b>내가 짠 가정을 내가 검증하는 꼴</b>이 된다.
 *
 * <h2>⚠️ 사전 준비</h2>
 * nori 를 넣은 이미지가 로컬에 있어야 한다. 없으면 {@code docker compose build elasticsearch}.
 * 공식 이미지로 바꾸면 형태소 테스트가 통째로 의미를 잃는다(분석기가 다르다).
 */
class FeedElasticsearchSearchTest extends IntegrationTestSupport {

    /** docker-compose 의 elasticsearch 서비스와 같은 이미지여야 한다. */
    private static final DockerImageName IMAGE = DockerImageName
            .parse("otboo-elasticsearch:8.18.8")
            .asCompatibleSubstituteFor("docker.elastic.co/elasticsearch/elasticsearch");

    /**
     * 기본 대기 전략(기동 로그 문자열 감시)을 쓰지 않는다. ES 8 은 콜드 스타트가 기본 제한시간
     * (60초)을 넘길 때가 있어 <b>느린 장비에서만 깨지는</b> 테스트가 된다. 게다가 그 전략은
     * 로그 형식에 기대는데, 우리가 필요한 것은 "로그에 뭐가 찍혔나" 가 아니라
     * <b>HTTP 로 질의가 되나</b> 다. 확인하려는 것을 그대로 확인한다.
     */
    static final ElasticsearchContainer ELASTICSEARCH = new ElasticsearchContainer(IMAGE)
            .withEnv("xpack.security.enabled", "false")
            .withEnv("discovery.type", "single-node")
            .withEnv("ES_JAVA_OPTS", "-Xms512m -Xmx512m")
            .waitingFor(new HttpWaitStrategy()
                    .forPort(9200)
                    .forPath("/_cluster/health?wait_for_status=yellow&timeout=30s")
                    .forStatusCode(200)
                    .withStartupTimeout(Duration.ofMinutes(3)));

    static {
        ELASTICSEARCH.start();
    }

    /**
     * 검색 엔진을 이 클래스에서만 다시 켠다. 기본값(mysql)은 {@code application.yml} 이 정하고,
     * {@code @DynamicPropertySource} 는 그보다 우선순위가 높다.
     *
     * <p>{@code index-on-startup=false} 인 이유 — 기동 시 자동 색인은 <b>컨텍스트가 뜰 때</b>
     * 한 번 돌아 그 시점의 빈 DB 를 색인한다. 픽스처는 그 뒤에 들어가므로 의미가 없고,
     * 테스트가 직접 {@code reindexAll()} 을 부르는 편이 시점이 분명하다.
     */
    @DynamicPropertySource
    static void elasticsearchProperties(DynamicPropertyRegistry registry) {
        registry.add("otboo.search.engine", () -> "elasticsearch");
        registry.add("otboo.search.index-on-startup", () -> false);
        registry.add("spring.elasticsearch.uris", ELASTICSEARCH::getHttpHostAddress);
    }

    @Autowired FeedSearchPort feedSearch;
    @Autowired FeedIndexManager indexManager;
    @Autowired UserRepository userRepository;
    @Autowired JdbcTemplate jdbc;

    private User author;
    private User otherAuthor;
    private UUID clearWeatherId;
    private UUID rainyWeatherId;

    @BeforeEach
    void setUp() throws Exception {
        jdbc.update("DELETE FROM feeds");
        jdbc.update("DELETE FROM weathers");
        userRepository.deleteAll();

        author = userRepository.save(User.create("author@otboo.com", "{noop}x", "류승지"));
        otherAuthor = userRepository.save(User.create("other@otboo.com", "{noop}x", "박교현"));
        clearWeatherId = insertWeather("CLEAR", "NONE");
        rainyWeatherId = insertWeather("CLOUDY", "RAIN");
    }

    @Nested
    @DisplayName("한국어 전문 검색")
    class FullText {

        @Test
        @DisplayName("형태소로 쪼개므로 합성어 안의 낱말로도 찾는다")
        void findsWordInsideCompound() throws Exception {
            UUID coat = insertFeed(author, clearWeatherId, "겨울코트를 꺼냈다");
            insertFeed(author, clearWeatherId, "반팔 티셔츠 하나로 충분한 날");
            reindex();

            assertThat(searchIds("코트")).containsExactly(coat);
        }

        @Test
        @DisplayName("띄어쓰기·순서가 달라도 낱말이 모두 있으면 찾는다 (MySQL LIKE 로는 못 찾던 것)")
        void findsAcrossWordBoundaries() throws Exception {
            UUID coat = insertFeed(author, clearWeatherId, "겨울코트를 꺼냈다");
            reindex();

            // LIKE '%겨울 코트%' 는 원문에 그 문자열이 없어 0건이었다.
            assertThat(searchIds("겨울 코트")).containsExactly(coat);
            assertThat(searchIds("코트 겨울")).containsExactly(coat);
        }

        @Test
        @DisplayName("낱말 중간에 걸친 부분 문자열도 ngram 으로 찾는다")
        void findsPartialSubstring() throws Exception {
            UUID coat = insertFeed(author, clearWeatherId, "겨울코트를 꺼냈다");
            reindex();

            assertThat(searchIds("울코")).containsExactly(coat);
        }

        @Test
        @DisplayName("낱말이 하나라도 빠지면 찾지 않는다 (검색이 필터 구실을 해야 한다)")
        void requiresAllWords() throws Exception {
            insertFeed(author, clearWeatherId, "겨울코트를 꺼냈다");
            reindex();

            assertThat(searchIds("겨울 패딩")).isEmpty();
        }

        @Test
        @DisplayName("본문뿐 아니라 작성자 이름으로도 찾는다")
        void findsByAuthorName() throws Exception {
            UUID mine = insertFeed(author, clearWeatherId, "아무 내용");
            insertFeed(otherAuthor, clearWeatherId, "남의 피드");
            reindex();

            assertThat(searchIds("류승지")).containsExactly(mine);
        }

        @Test
        @DisplayName("연산자 문자만 든 검색어는 조건 없이 전체를 돌려준다 (500 이 나면 안 된다)")
        void toleratesOperatorOnlyKeyword() throws Exception {
            insertFeed(author, clearWeatherId, "첫 번째");
            insertFeed(author, clearWeatherId, "두 번째");
            reindex();

            assertThat(search("+++", null, null, "createdAt").totalCount()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("필터")
    class Filters {

        @Test
        @DisplayName("날씨·강수·작성자 조건이 검색어와 함께 걸린다")
        void combinesFilters() throws Exception {
            UUID rainy = insertFeed(author, rainyWeatherId, "비 오는 날 코트");
            insertFeed(author, clearWeatherId, "맑은 날 코트");
            insertFeed(otherAuthor, rainyWeatherId, "남의 비 오는 날 코트");
            reindex();

            FeedSearchResultIds result = new FeedSearchResultIds(
                    search("코트", "CLOUDY", author.getId(), "createdAt"));
            assertThat(result.ids()).containsExactly(rainy);
        }
    }

    @Nested
    @DisplayName("커서 페이지네이션")
    class Pagination {

        @Test
        @DisplayName("createdAt 순으로 끝까지 넘겨도 중복·누락이 없다")
        void walksByCreatedAt() throws Exception {
            Set<UUID> inserted = insertMany();
            reindex();

            assertThat(walkAllPages("createdAt", 5)).containsExactlyInAnyOrderElementsOf(inserted);
        }

        @Test
        @DisplayName("likeCount 순으로 끝까지 넘겨도 중복·누락이 없다")
        void walksByLikeCount() throws Exception {
            Set<UUID> inserted = insertMany();
            reindex();

            assertThat(walkAllPages("likeCount", 5)).containsExactlyInAnyOrderElementsOf(inserted);
        }

        @Test
        @DisplayName("같은 밀리초에 만들어진 피드도 건너뛰지 않는다")
        void keepsSubMillisecondOrder() throws Exception {
            // DATETIME(6) 은 마이크로초까지 담는다. ES 의 createdAt 을 date(밀리초)로 매핑하면
            // 이 세 건이 같은 값이 되어 커서가 통째로 밀린다. date_nanos 라 구분된다.
            LocalDateTime base = LocalDateTime.of(2026, 9, 10, 5, 0, 0, 123_000_000);
            Set<UUID> inserted = new LinkedHashSet<>();
            for (int i = 0; i < 3; i++) {
                inserted.add(insertFeedAt(author, clearWeatherId, "동시각 " + i, 0,
                        base.plusNanos(i * 1_000L)));
            }
            reindex();

            assertThat(walkAllPages("createdAt", 1))
                    .containsExactlyInAnyOrderElementsOf(inserted);
        }
    }

    // ─────────────────────────── 헬퍼 ───────────────────────────

    /** 색인은 비동기라 테스트에서는 직접 채우고 refresh 한다. 시간을 기다리는 테스트는 깨진다. */
    private void reindex() throws Exception {
        indexManager.reindexAll();
        indexManager.refresh();
    }

    private List<UUID> searchIds(String keyword) {
        return search(keyword, null, null, "createdAt").feedIds();
    }

    private FeedSearchPort.FeedSearchResult search(
            String keyword, String skyStatus, UUID authorId, String sortBy) {
        return feedSearch.search(FeedSearchCondition.of(
                new CursorRequest(null, null, 50, sortBy, SortDirection.DESCENDING),
                keyword, skyStatus, null, authorId));
    }

    /** 첫 페이지부터 {@code hasNext} 가 끝날 때까지 실제 커서를 이어 붙여 훑는다. */
    private List<UUID> walkAllPages(String sortBy, int limit) {
        List<UUID> collected = new ArrayList<>();
        String cursor = null;
        UUID idAfter = null;

        for (int guard = 0; guard < 100; guard++) {
            FeedSearchPort.FeedSearchResult result = feedSearch.search(FeedSearchCondition.of(
                    new CursorRequest(cursor, idAfter, limit, sortBy, SortDirection.DESCENDING),
                    null, null, null, null));

            List<UUID> ids = result.feedIds();
            boolean hasNext = ids.size() > limit;
            List<UUID> page = hasNext ? ids.subList(0, limit) : ids;
            collected.addAll(page);

            if (!hasNext || page.isEmpty()) {
                return collected;
            }
            UUID last = page.getLast();
            cursor = cursorValueOf(last, sortBy);
            idAfter = last;
        }
        throw new IllegalStateException("페이지가 끝나지 않는다. 커서 조건을 확인해야 한다.");
    }

    /**
     * 다음 커서. 운영에서는 {@code CursorResponse} 가 <b>MySQL 이 돌려준 DTO</b> 에서 뽑으므로
     * 테스트도 같은 출처(MySQL)에서 읽어야 실제와 같은 경로를 검증한다.
     */
    private String cursorValueOf(UUID feedId, String sortBy) {
        if ("likeCount".equals(sortBy)) {
            return String.valueOf(jdbc.queryForObject(
                    "SELECT like_count FROM feeds WHERE id = ?", Long.class, feedId.toString()));
        }
        LocalDateTime createdAt = jdbc.queryForObject(
                "SELECT created_at FROM feeds WHERE id = ?", LocalDateTime.class,
                feedId.toString());
        return createdAt.toInstant(ZoneOffset.UTC).toString();
    }

    private Set<UUID> insertMany() {
        LocalDateTime base = LocalDateTime.of(2026, 9, 10, 0, 0);
        Set<UUID> ids = new LinkedHashSet<>();
        for (int i = 0; i < 23; i++) {
            ids.add(insertFeedAt(author, clearWeatherId, "피드 " + i, i, base.plusSeconds(i)));
        }
        return ids;
    }

    private UUID insertFeed(User owner, UUID weatherId, String content) {
        return insertFeedAt(owner, weatherId, content, 0, LocalDateTime.now(ZoneOffset.UTC));
    }

    private UUID insertFeedAt(
            User owner, UUID weatherId, String content, long likeCount, LocalDateTime createdAt) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO feeds (id, author_id, weather_id, content, like_count, comment_count,
                                   created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, 0, ?, ?)
                """, id.toString(), owner.getId().toString(), weatherId.toString(),
                content, likeCount, createdAt, createdAt);
        return id;
    }

    private UUID insertWeather(String skyStatus, String precipitationType) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO weathers (id, grid_x, grid_y, forecasted_at, forecast_at, sky_status,
                                      precipitation_type, precipitation_amount,
                                      precipitation_probability, humidity_current,
                                      temperature_current, temperature_min, temperature_max,
                                      wind_speed, wind_speed_as_word, created_at, updated_at)
                VALUES (?, 60, 127, NOW(6), NOW(6), ?, ?, 0, 0, 50, 18.5, 15, 22, 1.5, 'WEAK',
                        NOW(6), NOW(6))
                """, id.toString(), skyStatus, precipitationType);
        return id;
    }

    /** {@code limit + 1} 건이 섞여 오는 결과에서 id 만 꺼내 읽기 좋게 감싼다. */
    private record FeedSearchResultIds(FeedSearchPort.FeedSearchResult result) {
        List<UUID> ids() {
            return result.feedIds();
        }
    }
}
