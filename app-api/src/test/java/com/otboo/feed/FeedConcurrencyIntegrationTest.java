package com.otboo.feed;

import static org.assertj.core.api.Assertions.assertThat;

import com.otboo.common.exception.BusinessException;
import com.otboo.common.exception.ErrorCode;
import com.otboo.common.security.AuthPrincipal;
import com.otboo.common.test.IntegrationTestSupport;
import com.otboo.feed.dto.CommentCreateRequest;
import com.otboo.feed.dto.FeedCreateRequest;
import com.otboo.feed.dto.FollowCreateRequest;
import com.otboo.feed.entity.Feed;
import com.otboo.feed.exception.CommentErrorCode;
import com.otboo.feed.exception.FeedErrorCode;
import com.otboo.feed.exception.FollowErrorCode;
import com.otboo.feed.repository.FeedRepository;
import com.otboo.feed.service.CommentService;
import com.otboo.feed.service.FeedLikeService;
import com.otboo.feed.service.FeedService;
import com.otboo.feed.service.FollowService;
import com.otboo.user.entity.Role;
import com.otboo.user.entity.User;
import com.otboo.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 좋아요 · 댓글 · 팔로우 · 피드 수정/삭제의 동시성 테스트.
 * <p>서비스 주석의 "동시 요청 방어" 는 순차 요청만으로는 검증 x.
 * <h2>MockMvc 가 아니라 서비스를 직접 부른다</h2>
 * 검증 대상은 트랜잭션 · 행 락 · 유니크 제약이 겹치는 지점이고, 그건 전부 서비스 경계 안에서 일어난다.
 * 각 요청의 결과는 <b>성공이거나 {@link BusinessException} 이어야 한다.
 * 그 밖의 예외 데드락, {@code ObjectOptimisticLockingFailureException} 등)는 핸들러에서 500 이 된다.
 * <h2>테스트 트랜잭션을 걸지 않는다</h2>
 * 요청마다 실제로 커밋되어야 락 경합이 재현된다. 대신 끝나고 직접 치운다.
 */
class FeedConcurrencyIntegrationTest extends IntegrationTestSupport {

    /** HikariCP 기본 풀 크기(10)를 넘기지 않는다. 넘기면 커넥션 대기로 경합이 흐려진다. */
    private static final int THREADS = 10;

    @Autowired FeedService feedService;
    @Autowired FeedLikeService feedLikeService;
    @Autowired CommentService commentService;
    @Autowired FollowService followService;
    @Autowired FeedRepository feedRepository;
    @Autowired UserRepository userRepository;
    @Autowired TransactionTemplate transactionTemplate;
    @Autowired JdbcTemplate jdbc;

    private User author;
    private UUID feedId;

    @BeforeEach
    void setUp() {
        clearFixtures();

        author = userRepository.save(User.create("author@otboo.com", "{noop}x", "작성자"));
        UUID weatherId = insertWeather();
        feedId = feedService.create(principal(author),
                new FeedCreateRequest(weatherId, List.of(), "동시성 테스트 피드")).id();
    }

    /** 컨테이너를 모든 테스트 클래스가 공유하므로 남긴 행이 다음 클래스를 깨뜨린다. */
    @AfterEach
    void tearDown() {
        clearFixtures();
    }

    private void clearFixtures() {
        // feeds 를 지우면 feed_likes · comments 는 CASCADE 로 따라간다.
        jdbc.update("DELETE FROM feeds");
        jdbc.update("DELETE FROM follows");
        jdbc.update("DELETE FROM weathers");
        userRepository.deleteAll();
    }

    //여러 사람이 같은 피드에 동시에 몰리는 시나리오/
    @Nested
    @DisplayName("여러 사람이 같은 피드에 몰림")
    class HotFeed {

        @Test
        @DisplayName("[B1] 여러 명이 동시에 좋아요를 눌러도 전부 성공하고 like_count 가 행 수와 같다")
        void differentUsersLikeAtOnce() {
            List<User> likers = saveUsers(THREADS);

            Outcome outcome = runConcurrently(THREADS, i ->
                    feedLikeService.like(principal(likers.get(i)), feedId));

            assertThat(outcome.failures()).isEmpty();
            assertThat(likeRows()).isEqualTo(THREADS);
            assertThat(likeCount()).isEqualTo(THREADS);
        }

        @Test
        @DisplayName("[B2] 여러 명이 동시에 댓글을 달아도 전부 성공하고 comment_count 가 행 수와 같다")
        void differentUsersCommentAtOnce() {
            List<User> commenters = saveUsers(THREADS);

            Outcome outcome = runConcurrently(THREADS, i -> commentService.create(
                    principal(commenters.get(i)), feedId, new CommentCreateRequest("댓글 " + i)));

            assertThat(outcome.failures()).isEmpty();
            assertThat(commentRows()).isEqualTo(THREADS);
            assertThat(commentCount()).isEqualTo(THREADS);
        }

        @Test
        @DisplayName("[B3] 좋아요와 취소가 섞여 들어와도 전부 성공하고 like_count 가 행 수와 같다")
        void likesAndUnlikesInterleaved() {
            List<User> users = saveUsers(THREADS);
            // 짝수 번째는 미리 눌러 두고 취소시킨다. 홀수 번째는 새로 누른다.
            for (int i = 0; i < THREADS; i += 2) {
                feedLikeService.like(principal(users.get(i)), feedId);
            }

            Outcome outcome = runConcurrently(THREADS, i -> {
                if (i % 2 == 0) {
                    feedLikeService.unlike(principal(users.get(i)), feedId);
                } else {
                    feedLikeService.like(principal(users.get(i)), feedId);
                }
            });

            assertThat(outcome.failures()).isEmpty();
            assertThat(likeRows()).isEqualTo(THREADS / 2);
            assertThat(likeCount()).isEqualTo(likeRows());
        }
    }

    //같은 사람이 같은 요청을 연달아 보내는 시나리오(더블클릭 · 재시도)./
    @Nested
    @DisplayName("같은 요청 연타")
    class RepeatedRequest {

        @Test
        @DisplayName("[A1] 같은 사람이 좋아요를 연타해도 한 번만 반영되고 나머지는 409")
        void sameUserLikesAtOnce() {
            User liker = saveUsers(1).getFirst();

            Outcome outcome = runConcurrently(THREADS, i ->
                    feedLikeService.like(principal(liker), feedId));

            assertThat(outcome.succeeded()).isEqualTo(1);
            assertOnlyBusinessErrors(outcome, FeedErrorCode.ALREADY_LIKED);
            assertThat(likeRows()).isEqualTo(1);
            assertThat(likeCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("[A2] 같은 사람이 좋아요 취소를 연타해도 한 번만 반영되고 나머지는 409")
        void sameUserUnlikesAtOnce() {
            User liker = saveUsers(1).getFirst();
            feedLikeService.like(principal(liker), feedId);

            Outcome outcome = runConcurrently(THREADS, i ->
                    feedLikeService.unlike(principal(liker), feedId));

            assertThat(outcome.succeeded()).isEqualTo(1);
            assertOnlyBusinessErrors(outcome, FeedErrorCode.NOT_LIKED);
            assertThat(likeRows()).isZero();
            assertThat(likeCount()).isZero();
        }

        @Test
        @DisplayName("[A3] 같은 사람을 팔로우 연타해도 한 건만 남고 나머지는 409")
        void sameFollowAtOnce() {
            User follower = saveUsers(1).getFirst();

            Outcome outcome = runConcurrently(THREADS, i -> followService.follow(
                    principal(follower), new FollowCreateRequest(author.getId())));

            assertThat(outcome.succeeded()).isEqualTo(1);
            assertOnlyBusinessErrors(outcome, FollowErrorCode.ALREADY_FOLLOWING);
            assertThat(count("SELECT COUNT(*) FROM follows")).isEqualTo(1);
        }

        @Test
        @DisplayName("[A4] 팔로우 취소를 연타해도 한 번만 지워지고 나머지는 404")
        void sameUnfollowAtOnce() {
            User follower = saveUsers(1).getFirst();
            UUID followId = followService.follow(
                    principal(follower), new FollowCreateRequest(author.getId())).id();

            Outcome outcome = runConcurrently(THREADS, i ->
                    followService.unfollow(principal(follower), followId));

            assertThat(outcome.succeeded()).isEqualTo(1);
            assertOnlyBusinessErrors(outcome, FollowErrorCode.NOT_FOUND);
            assertThat(count("SELECT COUNT(*) FROM follows")).isZero();
        }

        @Test
        @DisplayName("[A5] 같은 댓글 삭제를 연타해도 한 번만 지워지고 나머지는 500 이 아니라 404")
        void sameCommentDeletedAtOnce() {
            UUID commentId = commentService.create(
                    principal(author), feedId, new CommentCreateRequest("지울 댓글")).id();

            Outcome outcome = runConcurrently(THREADS, i ->
                    commentService.delete(principal(author), feedId, commentId));

            assertThat(outcome.succeeded()).isEqualTo(1);
            assertOnlyBusinessErrors(outcome, CommentErrorCode.NOT_FOUND);
            assertThat(commentRows()).isZero();
            assertThat(commentCount()).isZero();
        }

        @Test
        @DisplayName("[A6] 같은 피드 삭제를 연타해도 한 번만 지워지고 나머지는 500 이 아니라 404")
        void sameFeedDeletedAtOnce() {
            Outcome outcome = runConcurrently(THREADS, i ->
                    feedService.delete(principal(author), feedId));

            assertThat(outcome.succeeded()).isEqualTo(1);
            assertOnlyBusinessErrors(outcome, FeedErrorCode.NOT_FOUND);
            assertThat(count("SELECT COUNT(*) FROM feeds WHERE id = ?", feedId.toString()))
                    .isZero();
        }
    }

    //서로 다른 작업이 같은 피드 행에 겹치는 시나리오./
    @Nested
    @DisplayName("서로 다른 작업이 겹침")
    class OverlappingWork {

        /**
         * {@code FeedService.update} 와 같은 순서(작성자와 함께 읽기 → 내용 변경 → flush),
         * but 읽기와 flush 사이</b>에 다른 요청이 커밋되게.
         *
         * <p>서비스 메서드를 그대로 동시에 부르면 그 틈이 수 ms 라 재현이 운에 맡겨진다.
         * 틈을 직접 벌리면 매번 같은 순서로 재현된다. 끼워 넣는 요청은 서로 경합하지 않도록
         * 한 스레드에서 차례로 보낸다 — 여기서 보려는 것은 수정과의 겹침 하나다.
         */
        @Test
        @DisplayName("[C1] 피드 수정 도중 좋아요·댓글이 커밋돼도 카운터가 옛 값으로 덮어써지지 않는다")
        void updateDoesNotOverwriteCounters() {
            List<User> users = saveUsers(3);

            transactionTemplate.executeWithoutResult(status -> {
                Feed feed = feedRepository.findWithAuthorById(feedId).orElseThrow();
                feed.updateContent("수정된 내용");

                // 수정 트랜잭션이 아직 열려 있는 동안 다른 트랜잭션에서 커밋한다.
                Outcome outcome = runConcurrently(1, ignored -> {
                    for (User user : users) {
                        feedLikeService.like(principal(user), feedId);
                    }
                    commentService.create(principal(users.getFirst()), feedId,
                            new CommentCreateRequest("수정 중에 달린 댓글"));
                });
                assertThat(outcome.failures()).isEmpty();

                feedRepository.flush();
            });

            assertThat(jdbc.queryForObject("SELECT content FROM feeds WHERE id = ?",
                    String.class, feedId.toString())).isEqualTo("수정된 내용");
            assertThat(likeCount()).isEqualTo(3);
            assertThat(commentCount()).isEqualTo(1);
        }
    }

    // ── 동시 실행 ────────────────────────────────────────────────────────────

    @FunctionalInterface
    private interface Request {
        void run(int index) throws Exception;
    }

    /** @param failures 성공하지 못한 요청이 던진 예외. 성공한 요청은 담기지 않는다 */
    private record Outcome(int succeeded, List<Throwable> failures) {
    }

    /**
     * 요청 {@code threads} 개를 전부 준비시킨 뒤 한꺼번에 출발시킨다.
     *
     * <p>스레드를 만들자마자 실행하면 앞 스레드가 끝난 뒤에 뒤 스레드가 출발해 사실상 순차 실행.
     * {@code ready} 로 전원이 출발선에 선 것을 확인하고 {@code start} 하나로 동시에 놓는다.
     */
    private Outcome runConcurrently(int threads, Request request) {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Throwable>> results = new ArrayList<>(threads);

        try {
            for (int i = 0; i < threads; i++) {
                int index = i;
                results.add(pool.submit(() -> {
                    ready.countDown();
                    start.await();
                    try {
                        request.run(index);
                        return null;
                    } catch (Throwable e) {
                        return e;
                    }
                }));
            }
            ready.await();
            start.countDown();

            List<Throwable> failures = new ArrayList<>();
            for (Future<Throwable> result : results) {
                Throwable failure = result.get(60, TimeUnit.SECONDS);
                if (failure != null) {
                    failures.add(failure);
                }
            }
            return new Outcome(threads - failures.size(), failures);
        } catch (Exception e) {
            throw new IllegalStateException("동시 요청이 제한 시간 안에 끝나지 않았다", e);
        } finally {
            pool.shutdownNow();
        }
    }

    /**하나라도 다른 예외면 그 요청은 500. */
    private static void assertOnlyBusinessErrors(Outcome outcome, ErrorCode expected) {
        assertThat(outcome.failures()).allSatisfy(failure ->
                assertThat(failure).isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(expected)));
    }

    // ── 픽스처 ───────────────────────────────────────────────────────────────

    private List<User> saveUsers(int size) {
        List<User> users = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            users.add(userRepository.save(
                    User.create("user" + i + "@otboo.com", "{noop}x", "사용자" + i)));
        }
        return users;
    }

    private AuthPrincipal principal(User user) {
        return new AuthPrincipal(user.getId(), user.getEmail(), Role.USER);
    }

    private UUID insertWeather() {
        UUID id = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        jdbc.update("""
                        INSERT INTO weathers (id, grid_x, grid_y, forecasted_at, forecast_at,
                                              sky_status, precipitation_type, precipitation_amount,
                                              precipitation_probability, humidity_current,
                                              temperature_current, temperature_min, temperature_max,
                                              wind_speed, wind_speed_as_word, created_at, updated_at)
                        VALUES (?, 60, 127, ?, ?, 'CLEAR', 'NONE', 0.0, 10.0, 55.0, 18.5, 12.0, 22.0,
                                1.5, 'WEAK', ?, ?)
                        """,
                id.toString(), now, now, now, now);
        return id;
    }

    private long likeCount() {
        return count("SELECT like_count FROM feeds WHERE id = ?", feedId.toString());
    }

    private long likeRows() {
        return count("SELECT COUNT(*) FROM feed_likes WHERE feed_id = ?", feedId.toString());
    }

    private long commentCount() {
        return count("SELECT comment_count FROM feeds WHERE id = ?", feedId.toString());
    }

    private long commentRows() {
        return count("SELECT COUNT(*) FROM comments WHERE feed_id = ?", feedId.toString());
    }

    private long count(String sql, Object... args) {
        Long value = jdbc.queryForObject(sql, Long.class, args);
        return value == null ? 0L : value;
    }
}
