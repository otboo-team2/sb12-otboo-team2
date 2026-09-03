package com.otboo.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.otboo.common.security.AuthPrincipal;
import com.otboo.common.security.LoginUser;
import com.otboo.user.entity.Role;
import com.otboo.user.entity.User;
import com.otboo.user.repository.RefreshTokenRepository;
import com.otboo.user.repository.UserRepository;
import com.otboo.common.test.IntegrationTestSupport;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@AutoConfigureMockMvc
class AuthIntegrationTest extends IntegrationTestSupport {

    private static final String PASSWORD = "Passw0rd!";

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired RefreshTokenRepository refreshTokenRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired ObjectMapper objectMapper;

    private User user;

    @BeforeEach
    void setUp() {
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
        user = userRepository.save(
                User.create("me@otboo.com", passwordEncoder.encode(PASSWORD), "여운정"));
    }

    private MvcResult signIn() throws Exception {
        return mockMvc.perform(multipart("/api/auth/sign-in")
                        .param("username", "me@otboo.com")
                        .param("password", PASSWORD)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andReturn();
    }

    @Nested
    @DisplayName("로그인")
    class SignIn {

        @Test
        @DisplayName("multipart 로 로그인하면 액세스 토큰과 리프레시 쿠키를 받는다")
        void success() throws Exception {
            mockMvc.perform(multipart("/api/auth/sign-in")
                            .param("username", "me@otboo.com")
                            .param("password", PASSWORD)
                            .with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").isNotEmpty())
                    .andExpect(jsonPath("$.userDto.email").value("me@otboo.com"))
                    .andExpect(jsonPath("$.userDto.role").value("USER"))
                    // 리프레시 토큰은 본문에 담지 않는다
                    .andExpect(jsonPath("$.refreshToken").doesNotExist())
                    .andExpect(cookie().exists("REFRESH_TOKEN"))
                    .andExpect(cookie().httpOnly("REFRESH_TOKEN", true));
        }

        @Test
        @DisplayName("JSON 으로 보내면 415 — 스펙이 multipart 다")
        void rejectsJson() throws Exception {
            mockMvc.perform(post("/api/auth/sign-in")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"me@otboo.com\",\"password\":\"" + PASSWORD + "\"}")
                            .with(csrf()))
                    .andExpect(status().isUnsupportedMediaType());
        }

        @Test
        @DisplayName("비밀번호가 틀려도 없는 계정과 같은 응답을 준다 — 가입 여부가 새면 안 된다")
        void wrongPasswordLooksLikeUnknownAccount() throws Exception {
            String wrongPassword = mockMvc.perform(multipart("/api/auth/sign-in")
                            .param("username", "me@otboo.com").param("password", "nope").with(csrf()))
                    .andExpect(status().isUnauthorized())
                    .andReturn().getResponse().getContentAsString();

            String unknownEmail = mockMvc.perform(multipart("/api/auth/sign-in")
                            .param("username", "nobody@otboo.com").param("password", "nope").with(csrf()))
                    .andExpect(status().isUnauthorized())
                    .andReturn().getResponse().getContentAsString();

            assertThat(wrongPassword).isEqualTo(unknownEmail);
            assertThat(wrongPassword).contains("AUTH_100");
        }

        @Test
        @DisplayName("잠긴 계정은 로그인할 수 없다")
        void lockedAccount() throws Exception {
            user.changeLocked(true);
            userRepository.save(user);

            mockMvc.perform(multipart("/api/auth/sign-in")
                            .param("username", "me@otboo.com").param("password", PASSWORD).with(csrf()))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.exceptionName").value("AUTH_110"));
        }
    }

    @Nested
    @DisplayName("토큰 재발급")
    class Reissue {

        @Test
        @DisplayName("재발급하면 리프레시 토큰이 회전한다")
        void rotates() throws Exception {
            Cookie refresh = signIn().getResponse().getCookie("REFRESH_TOKEN");

            MvcResult reissued = mockMvc.perform(post("/api/auth/refresh").cookie(refresh).with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").isNotEmpty())
                    .andReturn();

            Cookie rotated = reissued.getResponse().getCookie("REFRESH_TOKEN");
            assertThat(rotated).isNotNull();
            assertThat(rotated.getValue()).isNotEqualTo(refresh.getValue());
        }

        @Test
        @DisplayName("이미 쓴 리프레시 토큰은 재사용할 수 없다 — 동시 재발급 경쟁 방지")
        void rejectsReusedToken() throws Exception {
            Cookie refresh = signIn().getResponse().getCookie("REFRESH_TOKEN");

            mockMvc.perform(post("/api/auth/refresh").cookie(refresh).with(csrf()))
                    .andExpect(status().isOk());

            mockMvc.perform(post("/api/auth/refresh").cookie(refresh).with(csrf()))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.exceptionName").value("AUTH_102"));
        }

        @Test
        @DisplayName("쿠키가 없으면 401 — 프론트가 무한 재시도하지 않도록 반드시 401 이어야 한다")
        void missingCookie() throws Exception {
            mockMvc.perform(post("/api/auth/refresh").with(csrf()))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.exceptionName").value("AUTH_101"));
        }

        @Test
        @DisplayName("로그아웃하면 그 리프레시 토큰은 죽는다")
        void signOutInvalidates() throws Exception {
            MvcResult signedIn = signIn();
            Cookie refresh = signedIn.getResponse().getCookie("REFRESH_TOKEN");
            String accessToken = objectMapper.readTree(
                    signedIn.getResponse().getContentAsString()).get("accessToken").asText();

            // 로그아웃은 인증이 필요하다. 스펙에도 401 이 정의돼 있다.
            mockMvc.perform(post("/api/auth/sign-out")
                            .header("Authorization", "Bearer " + accessToken)
                            .cookie(refresh).with(csrf()))
                    .andExpect(status().isNoContent());

            mockMvc.perform(post("/api/auth/refresh").cookie(refresh).with(csrf()))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("접근 제어와 @LoginUser")
    class Access {

        @Test
        @DisplayName("토큰 없이 보호된 API 를 부르면 공통 형식의 401 이 온다")
        void unauthenticated() throws Exception {
            mockMvc.perform(post("/api/test/whoami")
                            .contentType(MediaType.APPLICATION_JSON).content("{}").with(csrf()))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.exceptionName").value("COMMON_100"));
        }

        @Test
        @DisplayName("본문의 authorId 를 위조해도 인증 주체가 주입된다")
        void bodyAuthorIdIsIgnored() throws Exception {
            String accessToken = objectMapper.readTree(
                    signIn().getResponse().getContentAsString()).get("accessToken").asText();

            UUID someoneElse = UUID.randomUUID();

            mockMvc.perform(post("/api/test/whoami")
                            .header("Authorization", "Bearer " + accessToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"authorId\":\"" + someoneElse + "\"}")
                            .with(csrf()))
                    .andExpect(status().isOk())
                    // 위조한 id 가 아니라 로그인한 사용자의 id 가 나와야 한다
                    .andExpect(jsonPath("$.actual").value(user.getId().toString()))
                    .andExpect(jsonPath("$.claimed").value(someoneElse.toString()));
        }

        @Test
        @DisplayName("일반 사용자는 어드민 API 에 접근할 수 없다")
        void nonAdminBlocked() throws Exception {
            String accessToken = objectMapper.readTree(
                    signIn().getResponse().getContentAsString()).get("accessToken").asText();

            mockMvc.perform(get("/api/users")
                            .header("Authorization", "Bearer " + accessToken)
                            .param("limit", "10").param("sortBy", "createdAt")
                            .param("sortDirection", "DESCENDING"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.exceptionName").value("COMMON_101"));
        }

        @Test
        @DisplayName("어드민은 통과한다")
        void adminAllowed() throws Exception {
            user.changeRole(Role.ADMIN);
            userRepository.save(user);
            String accessToken = objectMapper.readTree(
                    signIn().getResponse().getContentAsString()).get("accessToken").asText();

            // 아직 컨트롤러가 없어 404 가 정상이다. 403 이 아니면 인가는 통과한 것.
            mockMvc.perform(get("/api/users")
                            .header("Authorization", "Bearer " + accessToken)
                            .param("limit", "10"))
                    .andExpect(status().isNotFound());
        }
    }

    record WhoAmIRequest(UUID authorId) {
    }

    record WhoAmIResponse(String actual, String claimed) {
    }

    /**
     * {@code @LoginUser} 동작 확인용. 컴포넌트 스캔에 잡히므로 별도 등록은 하지 않는다.
     * {@code @Bean} 으로 한 번 더 올리면 매핑이 중복돼 컨텍스트가 뜨지 않는다.
     */
    @RestController
    static class WhoAmIController {

        @PostMapping("/api/test/whoami")
        WhoAmIResponse whoAmI(@LoginUser AuthPrincipal me, @RequestBody WhoAmIRequest request) {
            return new WhoAmIResponse(
                    me.userId().toString(),
                    request.authorId() == null ? null : request.authorId().toString());
        }
    }
}
