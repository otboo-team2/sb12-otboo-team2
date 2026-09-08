package com.otboo.feed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.otboo.common.security.AuthPrincipal;
import com.otboo.common.test.IntegrationTestSupport;
import com.otboo.feed.dto.FollowCreateRequest;
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
 * 팔로우 API 통합 테스트.
 *
 * <p>조회가 전부 손으로 쓴 SQL 이라 컴파일만으로는 아무것도 보장되지 않는다.
 * 특히 <b>follower/followee 방향</b>, 커서 페이징, {@code nameLike} 의 {@code ESCAPE} 는
 * 실제로 MySQL 에 던져봐야 확인된다.
 */
@AutoConfigureMockMvc
class FollowApiIntegrationTest extends IntegrationTestSupport {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired JdbcTemplate jdbc;

    private User me;
    private User other;
    private User third;

    @BeforeEach
    void setUp() {
        clearFixtures();

        me = userRepository.save(User.create("me@otboo.com", "{noop}x", "류승지"));
        other = userRepository.save(User.create("other@otboo.com", "{noop}x", "박교현"));
        third = userRepository.save(User.create("third@otboo.com", "{noop}x", "여운정"));

        insertProfile(other, "https://cdn.otboo.com/other.png");
    }

    /** 컨테이너를 모든 테스트 클래스가 공유하므로 남긴 행이 다음 클래스를 깨뜨린다. */
    @AfterEach
    void tearDown() {
        clearFixtures();
    }

    private void clearFixtures() {
        // follows·profiles 는 users 를 CASCADE 로 참조하지만 순서를 명시해 의도를 남긴다.
        jdbc.update("DELETE FROM follows");
        jdbc.update("DELETE FROM profiles");
        userRepository.deleteAll();
    }

    @Nested
    @DisplayName("팔로우 생성")
    class Create {

        @Test
        @DisplayName("양쪽 사용자가 채워진 FollowDto 를 돌려준다")
        void createsFollow() throws Exception {
            mockMvc.perform(authed(post("/api/follows"), me)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(new FollowCreateRequest(other.getId()))))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").exists())
                    .andExpect(jsonPath("$.follower.userId").value(me.getId().toString()))
                    .andExpect(jsonPath("$.follower.name").value("류승지"))
                    // 프로필이 없는 사용자는 이미지가 null 이어야 한다(LEFT JOIN)
                    .andExpect(jsonPath("$.follower.profileImageUrl").doesNotExist())
                    .andExpect(jsonPath("$.followee.userId").value(other.getId().toString()))
                    .andExpect(jsonPath("$.followee.name").value("박교현"))
                    .andExpect(jsonPath("$.followee.profileImageUrl")
                            .value("https://cdn.otboo.com/other.png"));
        }

        @Test
        @DisplayName("본문에 followerId 를 끼워 넣어도 토큰의 사용자가 팔로워가 된다")
        void ignoresFollowerIdInBody() throws Exception {
            // 프론트는 스펙대로 followerId 를 보낸다. DTO 에 그 필드가 없으므로 무시돼야 하고,
            // 알 수 없는 필드 때문에 400 이 나서도 안 된다.
            String body = """
                    {"followerId":"%s","followeeId":"%s"}
                    """.formatted(third.getId(), other.getId());

            mockMvc.perform(authed(post("/api/follows"), me)
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.follower.userId").value(me.getId().toString()));
        }

        @Test
        @DisplayName("자기 자신은 팔로우할 수 없다 - DB CHECK 까지 가기 전에 400")
        void rejectsSelfFollow() throws Exception {
            mockMvc.perform(authed(post("/api/follows"), me)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(new FollowCreateRequest(me.getId()))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.exceptionName").value("FOLLOW_001"));

            assertThat(count("follows")).isZero();
        }

        @Test
        @DisplayName("이미 팔로우한 사용자는 409")
        void rejectsDuplicate() throws Exception {
            follow(me, other);

            mockMvc.perform(authed(post("/api/follows"), me)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(new FollowCreateRequest(other.getId()))))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.exceptionName").value("FOLLOW_300"));

            assertThat(count("follows")).isEqualTo(1);
        }

        @Test
        @DisplayName("없는 사용자는 404")
        void rejectsUnknownFollowee() throws Exception {
            mockMvc.perform(authed(post("/api/follows"), me)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(new FollowCreateRequest(UUID.randomUUID()))))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.exceptionName").value("USER_200"));
        }

        @Test
        @DisplayName("followeeId 가 없으면 400")
        void rejectsMissingFollowee() throws Exception {
            mockMvc.perform(authed(post("/api/follows"), me)
                            .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("팔로우 취소")
    class Cancel {

        @Test
        @DisplayName("취소하면 204 이고 행이 사라진다")
        void cancels() throws Exception {
            UUID followId = follow(me, other);

            mockMvc.perform(authed(delete("/api/follows/" + followId), me))
                    .andExpect(status().isNoContent());

            assertThat(count("follows")).isZero();
        }

        @Test
        @DisplayName("남이 한 팔로우는 취소할 수 없다 - followId 만 알면 끊기는 것을 막는다")
        void rejectsOthersFollow() throws Exception {
            UUID followId = follow(other, third);

            mockMvc.perform(authed(delete("/api/follows/" + followId), me))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.exceptionName").value("FOLLOW_100"));

            assertThat(count("follows")).isEqualTo(1);
        }

        @Test
        @DisplayName("없는 팔로우는 404")
        void rejectsUnknown() throws Exception {
            mockMvc.perform(authed(delete("/api/follows/" + UUID.randomUUID()), me))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.exceptionName").value("FOLLOW_200"));
        }
    }

    @Nested
    @DisplayName("팔로잉 · 팔로워 목록")
    class Lists {

        @Test
        @DisplayName("팔로잉은 내가 팔로우한 사람이 나온다 - 방향이 뒤집히지 않는다")
        void listsFollowings() throws Exception {
            follow(me, other);
            follow(me, third);
            follow(third, me);  // 반대 방향은 섞이면 안 된다

            JsonNode body = readJson(mockMvc.perform(
                            authed(get("/api/follows/followings")
                                    .param("followerId", me.getId().toString())
                                    .param("limit", "10"), me))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(2))
                    .andExpect(jsonPath("$.totalCount").value(2))
                    .andExpect(jsonPath("$.hasNext").value(false))
                    .andExpect(jsonPath("$.sortBy").value("createdAt"))
                    .andExpect(jsonPath("$.sortDirection").value("DESCENDING")));

            for (JsonNode row : body.get("data")) {
                assertThat(row.get("follower").get("userId").asText())
                        .isEqualTo(me.getId().toString());
            }
        }

        @Test
        @DisplayName("팔로워는 나를 팔로우한 사람이 나온다")
        void listsFollowers() throws Exception {
            follow(other, me);
            follow(third, me);
            follow(me, other);  // 반대 방향

            JsonNode body = readJson(mockMvc.perform(
                            authed(get("/api/follows/followers")
                                    .param("followeeId", me.getId().toString())
                                    .param("limit", "10"), me))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(2))
                    .andExpect(jsonPath("$.totalCount").value(2)));

            for (JsonNode row : body.get("data")) {
                assertThat(row.get("followee").get("userId").asText())
                        .isEqualTo(me.getId().toString());
            }
        }

        @Test
        @DisplayName("nameLike 는 상대방 이름에 걸린다")
        void filtersByName() throws Exception {
            follow(me, other);   // 박교현
            follow(me, third);   // 여운정

            mockMvc.perform(authed(get("/api/follows/followings")
                            .param("followerId", me.getId().toString())
                            .param("limit", "10")
                            .param("nameLike", "교현"), me))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(1))
                    .andExpect(jsonPath("$.totalCount").value(1))
                    .andExpect(jsonPath("$.data[0].followee.name").value("박교현"));
        }

        @Test
        @DisplayName("nameLike 의 % 는 와일드카드가 아니라 글자로 취급된다")
        void escapesWildcard() throws Exception {
            User percent = userRepository.save(User.create("p@otboo.com", "{noop}x", "100%충전"));
            follow(me, other);
            follow(me, percent);

            // ESCAPE 가 없으면 '%' 가 전부와 일치해 두 건이 나온다.
            mockMvc.perform(authed(get("/api/follows/followings")
                            .param("followerId", me.getId().toString())
                            .param("limit", "10")
                            .param("nameLike", "%"), me))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(1))
                    .andExpect(jsonPath("$.data[0].followee.name").value("100%충전"));
        }

        @Test
        @DisplayName("커서로 다음 페이지를 이어 받는다 - 중복도 누락도 없다")
        void pagesWithCursor() throws Exception {
            follow(me, other);
            follow(me, third);

            JsonNode first = readJson(mockMvc.perform(
                            authed(get("/api/follows/followings")
                                    .param("followerId", me.getId().toString())
                                    .param("limit", "1"), me))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(1))
                    .andExpect(jsonPath("$.hasNext").value(true))
                    .andExpect(jsonPath("$.totalCount").value(2)));

            String firstId = first.get("data").get(0).get("id").asText();

            JsonNode second = readJson(mockMvc.perform(
                            authed(get("/api/follows/followings")
                                    .param("followerId", me.getId().toString())
                                    .param("limit", "1")
                                    .param("cursor", first.get("nextCursor").asText())
                                    .param("idAfter", first.get("nextIdAfter").asText()), me))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(1))
                    .andExpect(jsonPath("$.hasNext").value(false)));

            assertThat(second.get("data").get(0).get("id").asText()).isNotEqualTo(firstId);
        }

        @Test
        @DisplayName("팔로우가 없으면 빈 목록")
        void emptyList() throws Exception {
            mockMvc.perform(authed(get("/api/follows/followings")
                            .param("followerId", me.getId().toString())
                            .param("limit", "10"), me))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(0))
                    .andExpect(jsonPath("$.totalCount").value(0))
                    .andExpect(jsonPath("$.hasNext").value(false));
        }
    }

    @Nested
    @DisplayName("팔로우 요약")
    class Summary {

        @Test
        @DisplayName("카운트는 대상 기준, 관계는 나 기준이다")
        void summarizes() throws Exception {
            UUID followId = follow(me, other);   // 내가 other 를 팔로우
            follow(other, me);                   // other 도 나를 팔로우 (맞팔)
            follow(third, other);                // other 의 팔로워 한 명 더

            mockMvc.perform(authed(get("/api/follows/summary")
                            .param("userId", other.getId().toString()), me))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.followeeId").value(other.getId().toString()))
                    // other 를 팔로우하는 사람: me, third
                    .andExpect(jsonPath("$.followerCount").value(2))
                    // other 가 팔로우하는 사람: me
                    .andExpect(jsonPath("$.followingCount").value(1))
                    .andExpect(jsonPath("$.followedByMe").value(true))
                    .andExpect(jsonPath("$.followedByMeId").value(followId.toString()))
                    .andExpect(jsonPath("$.followingMe").value(true));
        }

        @Test
        @DisplayName("팔로우하지 않았으면 followedByMeId 는 비어 있다")
        void notFollowing() throws Exception {
            mockMvc.perform(authed(get("/api/follows/summary")
                            .param("userId", other.getId().toString()), me))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.followerCount").value(0))
                    .andExpect(jsonPath("$.followingCount").value(0))
                    .andExpect(jsonPath("$.followedByMe").value(false))
                    .andExpect(jsonPath("$.followedByMeId").doesNotExist())
                    .andExpect(jsonPath("$.followingMe").value(false));
        }

        @Test
        @DisplayName("없는 사용자는 404 - 팔로워 0 명으로 보여주지 않는다")
        void rejectsUnknownUser() throws Exception {
            mockMvc.perform(authed(get("/api/follows/summary")
                            .param("userId", UUID.randomUUID().toString()), me))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.exceptionName").value("USER_200"));
        }
    }

    private UUID follow(User follower, User followee) throws Exception {
        JsonNode created = readJson(mockMvc.perform(authed(post("/api/follows"), follower)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new FollowCreateRequest(followee.getId()))))
                .andExpect(status().isCreated()));
        return UUID.fromString(created.get("id").asText());
    }

    private void insertProfile(User user, String imageUrl) {
        jdbc.update("""
                INSERT INTO profiles (id, user_id, profile_image_url, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?)
                """, UUID.randomUUID().toString(), user.getId().toString(), imageUrl, now(), now());
    }

    private MockHttpServletRequestBuilder authed(MockHttpServletRequestBuilder builder, User user) {
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                new AuthPrincipal(user.getId(), user.getEmail(), Role.USER), null,
                List.of(new SimpleGrantedAuthority(Role.USER.authority())));
        return builder.with(authentication(authentication)).with(csrf());
    }

    private LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    private long count(String table) {
        Long value = jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
        return value == null ? 0L : value;
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private JsonNode readJson(ResultActions actions) throws Exception {
        return objectMapper.readTree(actions.andReturn().getResponse().getContentAsString());
    }
}
