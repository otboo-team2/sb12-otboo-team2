package com.otboo.feed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.otboo.common.security.AuthPrincipal;
import com.otboo.common.test.IntegrationTestSupport;
import com.otboo.feed.dto.CommentCreateRequest;
import com.otboo.feed.dto.FeedCreateRequest;
import com.otboo.feed.dto.FeedUpdateRequest;
import com.otboo.user.entity.Role;
import com.otboo.user.entity.User;
import com.otboo.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 피드 · 좋아요 · 댓글 API 통합 테스트.
 *
 * <h2>날씨·의상은 SQL 로 직접 넣는다</h2>
 * 두 도메인의 엔티티는 다른 파트 소유라 아직 없다. 피드는 그 테이블들을 FK 로 참조하므로
 * 테스트 데이터가 반드시 있어야 한다. 엔티티가 생기면 이 부분만 리포지터리 호출로 바꾸면 된다.
 */
@AutoConfigureMockMvc
class FeedApiIntegrationTest extends IntegrationTestSupport {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired JdbcTemplate jdbc;

    private User me;
    private User other;
    private UUID weatherId;
    private UUID rainyWeatherId;
    private UUID myTopId;
    private UUID othersTopId;

    @BeforeEach
    void setUp() {
        clearFixtures();

        me = userRepository.save(User.create("me@otboo.com", "{noop}x", "류승지"));
        other = userRepository.save(User.create("other@otboo.com", "{noop}x", "박교현"));

        weatherId = insertWeather("CLEAR", "NONE");
        rainyWeatherId = insertWeather("CLOUDY", "RAIN");
        myTopId = insertClothes(me.getId(), "회색 맨투맨", "TOP");
        othersTopId = insertClothes(other.getId(), "남의 코트", "OUTER");
    }

    /**
     * <b>끝나고도 반드시 치운다.</b> 통합 테스트는 MySQL 컨테이너 하나를 모든 클래스가 공유하므로
     * (IntegrationTestSupport 의 static 컨테이너) 남긴 행이 <b>다음 테스트 클래스를 깨뜨린다.</b>
     *
     * <p>실제로 겪은 사고: 이 클래스가 clothes 를 남긴 채 끝나면, 뒤이어 도는
     * {@code UserSignUpIntegrationTest} 의 {@code userRepository.deleteAll()} 이
     * {@code fk_clothes_owner}(ON DELETE RESTRICT) 에 걸려 12개가 무더기로 실패했다.
     * 자기 클래스만 돌리면 통과하고 전체를 돌리면 깨져서 원인을 찾기 어려운 종류다.
     */
    @AfterEach
    void tearDown() {
        clearFixtures();
    }

    private void clearFixtures() {
        // feeds 를 지우면 feed_clothes·feed_likes·comments 는 CASCADE 로 따라간다.
        // clothes 는 users 를 RESTRICT 로 참조하므로 users 보다 먼저 지워야 한다.
        jdbc.update("DELETE FROM feeds");
        jdbc.update("DELETE FROM clothes");
        jdbc.update("DELETE FROM weathers");
        userRepository.deleteAll();
    }

    @Nested
    @DisplayName("피드 등록")
    class Create {

        @Test
        @DisplayName("작성자·날씨·착장이 채워진 FeedDto 를 돌려준다")
        void createsFeed() throws Exception {
            mockMvc.perform(authed(post("/api/feeds"), me)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(new FeedCreateRequest(
                                    weatherId, List.of(myTopId), "오늘은 맨투맨 하나로 충분했다"))))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.content").value("오늘은 맨투맨 하나로 충분했다"))
                    .andExpect(jsonPath("$.author.userId").value(me.getId().toString()))
                    .andExpect(jsonPath("$.author.name").value("류승지"))
                    .andExpect(jsonPath("$.weather.weatherId").value(weatherId.toString()))
                    .andExpect(jsonPath("$.weather.skyStatus").value("CLEAR"))
                    .andExpect(jsonPath("$.weather.temperature.current").value(18.5))
                    .andExpect(jsonPath("$.ootds[0].clothesId").value(myTopId.toString()))
                    .andExpect(jsonPath("$.ootds[0].name").value("회색 맨투맨"))
                    .andExpect(jsonPath("$.likeCount").value(0))
                    .andExpect(jsonPath("$.commentCount").value(0))
                    .andExpect(jsonPath("$.likedByMe").value(false));
        }

        @Test
        @DisplayName("본문에 authorId 를 끼워 넣어도 토큰의 사용자가 작성자가 된다")
        void ignoresAuthorIdInBody() throws Exception {
            // 프론트는 스펙대로 authorId 를 보낸다. DTO 에 그 필드가 없으므로 무시돼야 하고,
            // 알 수 없는 필드 때문에 400 이 나서도 안 된다.
            String body = """
                    {"authorId":"%s","weatherId":"%s","clothesIds":[],"content":"위조 시도"}
                    """.formatted(other.getId(), weatherId);

            mockMvc.perform(authed(post("/api/feeds"), me)
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.author.userId").value(me.getId().toString()));
        }

        @Test
        @DisplayName("남의 옷을 착장에 넣으면 403")
        void rejectsOthersClothes() throws Exception {
            mockMvc.perform(authed(post("/api/feeds"), me)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(new FeedCreateRequest(
                                    weatherId, List.of(othersTopId), "남의 옷"))))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.exceptionName").value("FEED_101"));
        }

        @Test
        @DisplayName("없는 날씨 id 면 404")
        void rejectsUnknownWeather() throws Exception {
            mockMvc.perform(authed(post("/api/feeds"), me)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(new FeedCreateRequest(
                                    UUID.randomUUID(), List.of(), "없는 날씨"))))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.exceptionName").value("FEED_201"));
        }

        @Test
        @DisplayName("같은 옷을 두 번 넣어도 유니크 제약에 걸리지 않고 한 벌로 저장된다")
        void deduplicatesClothes() throws Exception {
            mockMvc.perform(authed(post("/api/feeds"), me)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(new FeedCreateRequest(
                                    weatherId, List.of(myTopId, myTopId), "중복 착장"))))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.ootds.length()").value(1));
        }
    }

    @Nested
    @DisplayName("피드 수정 · 삭제")
    class ModifyAndDelete {

        @Test
        @DisplayName("작성자만 수정할 수 있다")
        void onlyAuthorCanUpdate() throws Exception {
            UUID feedId = createFeed(me, weatherId, "원본");

            mockMvc.perform(authed(patch("/api/feeds/" + feedId), other)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(new FeedUpdateRequest("남이 고침"))))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.exceptionName").value("FEED_100"));

            mockMvc.perform(authed(patch("/api/feeds/" + feedId), me)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(new FeedUpdateRequest("내가 고침"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").value("내가 고침"));
        }

        @Test
        @DisplayName("삭제하면 댓글·좋아요도 함께 사라진다")
        void deleteCascades() throws Exception {
            UUID feedId = createFeed(me, weatherId, "지울 피드");
            like(other, feedId);
            comment(other, feedId, "댓글");

            mockMvc.perform(authed(delete("/api/feeds/" + feedId), me))
                    .andExpect(status().isNoContent());

            assertThat(count("feeds")).isZero();
            assertThat(count("feed_likes")).isZero();
            assertThat(count("comments")).isZero();
        }
    }

    @Nested
    @DisplayName("좋아요")
    class Like {

        @Test
        @DisplayName("좋아요 수가 오르고 누른 사람에게만 likedByMe 가 켜진다")
        void likeAndUnlike() throws Exception {
            UUID feedId = createFeed(me, weatherId, "좋아요 대상");

            mockMvc.perform(authed(post("/api/feeds/" + feedId + "/like"), other))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.likeCount").value(1))
                    .andExpect(jsonPath("$.likedByMe").value(true));

            mockMvc.perform(authed(get("/api/feeds"), me)
                            .param("limit", "10").param("sortBy", "createdAt")
                            .param("sortDirection", "DESCENDING"))
                    .andExpect(jsonPath("$.data[0].likeCount").value(1))
                    .andExpect(jsonPath("$.data[0].likedByMe").value(false));

            mockMvc.perform(authed(delete("/api/feeds/" + feedId + "/like"), other))
                    .andExpect(status().isNoContent());

            assertThat(likeCountOf(feedId)).isZero();
        }

        @Test
        @DisplayName("같은 피드에 두 번 좋아요를 누르면 409 이고 수가 늘지 않는다")
        void rejectsDuplicateLike() throws Exception {
            UUID feedId = createFeed(me, weatherId, "중복 좋아요");
            like(other, feedId);

            mockMvc.perform(authed(post("/api/feeds/" + feedId + "/like"), other))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.exceptionName").value("FEED_300"));

            assertThat(likeCountOf(feedId)).isEqualTo(1L);
        }

        @Test
        @DisplayName("누르지 않은 좋아요를 취소하면 409 이고 수가 음수로 내려가지 않는다")
        void rejectsUnlikeWithoutLike() throws Exception {
            UUID feedId = createFeed(me, weatherId, "취소만");

            mockMvc.perform(authed(delete("/api/feeds/" + feedId + "/like"), other))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.exceptionName").value("FEED_301"));

            assertThat(likeCountOf(feedId)).isZero();
        }
    }

    @Nested
    @DisplayName("댓글")
    class Comments {

        @Test
        @DisplayName("등록하면 commentCount 가 오르고 최신순으로 조회된다")
        void createAndList() throws Exception {
            UUID feedId = createFeed(me, weatherId, "댓글 달릴 피드");

            comment(other, feedId, "첫 번째");
            comment(other, feedId, "두 번째");

            mockMvc.perform(authed(get("/api/feeds/" + feedId + "/comments"), me)
                            .param("limit", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalCount").value(2))
                    .andExpect(jsonPath("$.data[0].content").value("두 번째"))
                    .andExpect(jsonPath("$.data[0].author.name").value("박교현"))
                    .andExpect(jsonPath("$.data[1].content").value("첫 번째"))
                    .andExpect(jsonPath("$.hasNext").value(false))
                    // 클라이언트가 안 보내도 응답은 실제 정렬을 설명해야 한다
                    .andExpect(jsonPath("$.sortBy").value("createdAt"))
                    .andExpect(jsonPath("$.sortDirection").value("DESCENDING"));
        }

        @Test
        @DisplayName("커서로 다음 페이지를 이어서 읽는다")
        void paginates() throws Exception {
            UUID feedId = createFeed(me, weatherId, "댓글 페이지네이션");
            for (int i = 1; i <= 5; i++) {
                comment(other, feedId, "댓글 " + i);
            }

            JsonNode first = readJson(
                    mockMvc.perform(authed(get("/api/feeds/" + feedId + "/comments"), me)
                                    .param("limit", "2"))
                            .andExpect(jsonPath("$.hasNext").value(true))
                            .andExpect(jsonPath("$.data.length()").value(2)));

            mockMvc.perform(authed(get("/api/feeds/" + feedId + "/comments"), me)
                            .param("limit", "2")
                            .param("cursor", first.get("nextCursor").asText())
                            .param("idAfter", first.get("nextIdAfter").asText()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].content").value("댓글 3"))
                    .andExpect(jsonPath("$.data[1].content").value("댓글 2"));
        }

        @Test
        @DisplayName("스펙 모양대로 feedId·authorId 를 함께 보내도 400 이 아니고, 작성자는 토큰의 사용자다")
        void acceptsSpecShapedBody() throws Exception {
            // 프론트(FeedComments.tsx)는 Swagger 스펙대로 feedId·authorId 를 함께 보낸다.
            // 우리 DTO 에는 그 필드가 없으므로 (1) 알 수 없는 필드로 400 이 나면 안 되고
            // (2) authorId 를 남의 것으로 넣어도 작성자가 바뀌면 안 된다.
            UUID feedId = createFeed(me, weatherId, "스펙 모양 댓글");
            String body = """
                    {"feedId":"%s","authorId":"%s","content":"스펙대로 보낸 댓글"}
                    """.formatted(feedId, other.getId());

            mockMvc.perform(authed(post("/api/feeds/" + feedId + "/comments"), me)
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").value("스펙대로 보낸 댓글"))
                    .andExpect(jsonPath("$.author.userId").value(me.getId().toString()));
        }

        @Test
        @DisplayName("작성자도 피드 주인도 아니면 삭제할 수 없다")
        void onlyAuthorOrFeedOwnerCanDelete() throws Exception {
            UUID feedId = createFeed(me, weatherId, "댓글 삭제");
            UUID commentId = comment(other, feedId, "지울 댓글");
            User stranger = userRepository.save(User.create("s@otboo.com", "{noop}x", "제3자"));

            mockMvc.perform(authed(
                            delete("/api/feeds/" + feedId + "/comments/" + commentId), stranger))
                    .andExpect(status().isForbidden());

            // 피드 주인이므로 지울 수 있다
            mockMvc.perform(authed(
                            delete("/api/feeds/" + feedId + "/comments/" + commentId), me))
                    .andExpect(status().isNoContent());
            assertThat(commentCountOf(feedId)).isZero();
        }
    }

    @Nested
    @DisplayName("목록 조회 · 검색")
    class Search {

        @Test
        @DisplayName("keywordLike 는 단어 중간에 있어도 찾는다 (ngram 전문검색)")
        void findsByPartialKeyword() throws Exception {
            createFeed(me, weatherId, "겨울코트를 꺼냈다");
            createFeed(me, weatherId, "반팔에 샌들 신고 나갔다");

            mockMvc.perform(feedSearch(me).param("keywordLike", "코트"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalCount").value(1))
                    .andExpect(jsonPath("$.data[0].content").value("겨울코트를 꺼냈다"));
        }

        @Test
        @DisplayName("ngram 토큰이 흩어져 걸린 피드는 LIKE 단계에서 걸러진다")
        void doesNotMatchScatteredTokens() throws Exception {
            // "겨울코트" 의 2그램은 겨울/울코/코트. 아래 문장은 "겨울" 과 "코트" 를 모두 갖고 있어
            // MATCH 단계는 통과하지만 실제 부분 문자열은 아니다.
            createFeed(me, weatherId, "겨울인데 코트 없이 나갔다");
            createFeed(me, weatherId, "겨울코트를 꺼냈다");

            mockMvc.perform(feedSearch(me).param("keywordLike", "겨울코트"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalCount").value(1))
                    .andExpect(jsonPath("$.data[0].content").value("겨울코트를 꺼냈다"));
        }

        @Test
        @DisplayName("검색어에 불리언 모드 연산자가 섞여도 실패하지 않는다")
        void toleratesOperatorCharacters() throws Exception {
            createFeed(me, weatherId, "오늘의 착장");

            mockMvc.perform(feedSearch(me).param("keywordLike", "+++착장***"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].content").value("오늘의 착장"));
        }

        @Test
        @DisplayName("한 글자 검색어도 결과가 나온다 (색인 토큰이 없어 LIKE 로 내려간다)")
        void findsBySingleCharacter() throws Exception {
            createFeed(me, weatherId, "봄 날씨");
            createFeed(me, weatherId, "여름 날씨");

            mockMvc.perform(feedSearch(me).param("keywordLike", "봄"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalCount").value(1))
                    .andExpect(jsonPath("$.data[0].content").value("봄 날씨"));
        }

        @Test
        @DisplayName("와일드카드를 입력해도 전체 조회가 되지 않는다")
        void treatsWildcardAsLiteral() throws Exception {
            createFeed(me, weatherId, "평범한 피드");

            mockMvc.perform(feedSearch(me).param("keywordLike", "%"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalCount").value(0));
        }

        @Test
        @DisplayName("날씨·작성자 필터가 걸린다")
        void filters() throws Exception {
            createFeed(me, weatherId, "맑은 날");
            createFeed(other, rainyWeatherId, "비 오는 날");

            mockMvc.perform(feedSearch(me).param("precipitationTypeEqual", "RAIN"))
                    .andExpect(jsonPath("$.totalCount").value(1))
                    .andExpect(jsonPath("$.data[0].content").value("비 오는 날"));

            mockMvc.perform(feedSearch(me).param("authorIdEqual", me.getId().toString()))
                    .andExpect(jsonPath("$.totalCount").value(1))
                    .andExpect(jsonPath("$.data[0].content").value("맑은 날"));
        }

        @Test
        @DisplayName("허용하지 않는 필터 값은 400 으로 거절한다")
        void rejectsUnknownFilterValue() throws Exception {
            mockMvc.perform(feedSearch(me).param("skyStatusEqual", "SUNNY"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.exceptionName").value("COMMON_001"));
        }

        @Test
        @DisplayName("커서 페이지네이션이 중복·누락 없이 이어진다")
        void paginatesWithoutGaps() throws Exception {
            for (int i = 1; i <= 5; i++) {
                createFeed(me, weatherId, "피드 " + i);
            }

            JsonNode first = readJson(mockMvc.perform(feedSearch(me, 2))
                    .andExpect(jsonPath("$.totalCount").value(5))
                    .andExpect(jsonPath("$.hasNext").value(true)));

            mockMvc.perform(feedSearch(me, 2)
                            .param("cursor", first.get("nextCursor").asText())
                            .param("idAfter", first.get("nextIdAfter").asText()))
                    .andExpect(jsonPath("$.data[0].content").value("피드 3"))
                    .andExpect(jsonPath("$.data[1].content").value("피드 2"));
        }

        @Test
        @DisplayName("좋아요순 정렬")
        void sortsByLikeCount() throws Exception {
            UUID quiet = createFeed(me, weatherId, "조용한 피드");
            UUID popular = createFeed(me, weatherId, "인기 피드");
            like(other, popular);

            mockMvc.perform(authed(get("/api/feeds"), me)
                            .param("limit", "10").param("sortBy", "likeCount")
                            .param("sortDirection", "DESCENDING"))
                    .andExpect(jsonPath("$.data[0].id").value(popular.toString()))
                    .andExpect(jsonPath("$.data[1].id").value(quiet.toString()));
        }
    }

    // ── 테스트 지원 ──────────────────────────────────────────────────────────

    /** 최신순 목록 조회의 공통 파라미터. 검색 테스트마다 반복하지 않으려고 묶었다. */
    private MockHttpServletRequestBuilder feedSearch(User viewer) {
        return feedSearch(viewer, 10);
    }

    /**
     * ⚠️ limit 을 여기서 받는 이유: {@code param()} 은 값을 덮어쓰지 않고 <b>덧붙인다.</b>
     * 헬퍼가 limit 을 고정해 두고 호출부에서 {@code .param("limit", "2")} 를 또 붙이면
     * {@code limit=10&limit=2} 가 되고 바인딩은 첫 값(10)을 쓴다 —
     * 페이지네이션 테스트가 조용히 무의미해진다.
     */
    private MockHttpServletRequestBuilder feedSearch(User viewer, int limit) {
        return authed(get("/api/feeds"), viewer)
                .param("limit", String.valueOf(limit))
                .param("sortBy", "createdAt")
                .param("sortDirection", "DESCENDING");
    }

    /**
     * 실제 필터 대신 SecurityContext 를 직접 채운다. 토큰 발급·검증은
     * {@code AuthIntegrationTest} 가 이미 검증하므로 여기서 다시 태울 이유가 없다.
     */
    private MockHttpServletRequestBuilder authed(MockHttpServletRequestBuilder builder, User user) {
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                new AuthPrincipal(user.getId(), user.getEmail(), Role.USER), null,
                List.of(new SimpleGrantedAuthority(Role.USER.authority())));
        return builder.with(authentication(authentication)).with(csrf());
    }

    private UUID createFeed(User author, UUID weather, String content) throws Exception {
        JsonNode created = readJson(mockMvc.perform(authed(post("/api/feeds"), author)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new FeedCreateRequest(weather, List.of(), content))))
                .andExpect(status().isCreated()));
        return UUID.fromString(created.get("id").asText());
    }

    private void like(User user, UUID feedId) throws Exception {
        mockMvc.perform(authed(post("/api/feeds/" + feedId + "/like"), user))
                .andExpect(status().isOk());
    }

    private UUID comment(User user, UUID feedId, String content) throws Exception {
        JsonNode created = readJson(
                mockMvc.perform(authed(post("/api/feeds/" + feedId + "/comments"), user)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(new CommentCreateRequest(content))))
                        .andExpect(status().isOk()));
        return UUID.fromString(created.get("id").asText());
    }

    private UUID insertWeather(String skyStatus, String precipitationType) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                        INSERT INTO weathers (id, grid_x, grid_y, forecasted_at, forecast_at,
                                              sky_status, precipitation_type, precipitation_amount,
                                              precipitation_probability, humidity_current,
                                              temperature_current, temperature_min, temperature_max,
                                              wind_speed, wind_speed_as_word, created_at, updated_at)
                        VALUES (?, 60, 127, ?, ?, ?, ?, 0.0, 10.0, 55.0, 18.5, 12.0, 22.0,
                                1.5, 'WEAK', ?, ?)
                        """,
                id.toString(), now(), now(), skyStatus, precipitationType, now(), now());
        return id;
    }

    private UUID insertClothes(UUID ownerId, String name, String type) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO clothes (id, owner_id, name, type, image_url, created_at, updated_at)
                VALUES (?, ?, ?, ?, NULL, ?, ?)
                """, id.toString(), ownerId.toString(), name, type, now(), now());
        return id;
    }

    private LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    private long count(String table) {
        Long value = jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
        return value == null ? 0L : value;
    }

    private long likeCountOf(UUID feedId) {
        Long value = jdbc.queryForObject(
                "SELECT like_count FROM feeds WHERE id = ?", Long.class, feedId.toString());
        return value == null ? 0L : value;
    }

    private long commentCountOf(UUID feedId) {
        Long value = jdbc.queryForObject(
                "SELECT comment_count FROM feeds WHERE id = ?", Long.class, feedId.toString());
        return value == null ? 0L : value;
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private JsonNode readJson(ResultActions actions) throws Exception {
        return objectMapper.readTree(actions.andReturn().getResponse().getContentAsString());
    }
}
