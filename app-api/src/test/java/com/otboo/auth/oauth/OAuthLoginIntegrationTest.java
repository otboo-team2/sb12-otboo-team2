package com.otboo.auth.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.otboo.auth.AuthService;
import com.otboo.auth.exception.AuthErrorCode;
import com.otboo.common.exception.BusinessException;
import com.otboo.common.test.IntegrationTestSupport;
import com.otboo.user.entity.OAuthProvider;
import com.otboo.user.entity.Role;
import com.otboo.user.entity.User;
import com.otboo.user.repository.ProfileRepository;
import com.otboo.user.repository.RefreshTokenRepository;
import com.otboo.user.repository.UserOAuthAccountRepository;
import com.otboo.user.repository.UserRepository;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

/** KAN — 소셜 로그인. 제공자 호출은 빼고 우리 규칙만 검증한다. */
@AutoConfigureMockMvc
class OAuthLoginIntegrationTest extends IntegrationTestSupport {

    @Autowired MockMvc mockMvc;
    @Autowired OAuthLoginService oauthLoginService;
    @Autowired AuthService authService;
    @Autowired UserRepository userRepository;
    @Autowired UserOAuthAccountRepository oauthAccountRepository;
    @Autowired ProfileRepository profileRepository;
    @Autowired RefreshTokenRepository refreshTokenRepository;
    @Autowired PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        refreshTokenRepository.deleteAllInBatch();
        oauthAccountRepository.deleteAllInBatch();
        profileRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    private static OAuthAttributes google(String sub, String email, boolean verified) {
        return OAuthAttributes.of(OAuthProvider.GOOGLE, Map.of(
                "sub", sub, "email", email, "email_verified", verified, "name", "구글사용자"));
    }

    @Nested
    @DisplayName("진입점")
    class Entry {

        @Test
        @DisplayName("인증 없이 /oauth2/authorization/google 이 구글로 넘긴다")
        void 구글로_리다이렉트한다() throws Exception {
            // STATELESS 세션 정책에서도 인증 요청이 저장되는지 확인한다.
            // 저장이 안 되면 콜백에서 authorization_request_not_found 가 난다.
            mockMvc.perform(get("/oauth2/authorization/google"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrlPattern("https://accounts.google.com/**"));
        }
    }

    @Nested
    @DisplayName("최초 로그인")
    class FirstLogin {

        @Test
        @DisplayName("계정과 빈 프로필을 함께 만든다")
        void 가입시킨다() {
            User user = oauthLoginService.login(
                    OAuthProvider.GOOGLE, google("g-1", "new@gmail.com", true));

            assertThat(user.getEmail()).isEqualTo("new@gmail.com");
            assertThat(user.getRole()).isEqualTo(Role.USER);
            // 소셜 전용 계정은 비밀번호가 없다 → 일반 로그인 경로에서 자동으로 막힌다
            assertThat(user.hasPassword()).isFalse();
            assertThat(profileRepository.findByUserId(user.getId())).isPresent();
            assertThat(oauthAccountRepository.findAllByUserId(user.getId())).hasSize(1);
        }

        @Test
        @DisplayName("이메일 대소문자가 달라도 같은 계정으로 본다")
        void 이메일을_소문자로_맞춘다() {
            User user = oauthLoginService.login(
                    OAuthProvider.GOOGLE, google("g-2", "Mixed@Gmail.com", true));

            assertThat(user.getEmail()).isEqualTo("mixed@gmail.com");
        }

        @Test
        @DisplayName("두 번째 로그인은 같은 계정을 돌려준다 — 매번 가입시키지 않는다")
        void 재로그인은_같은_계정() {
            User first = oauthLoginService.login(
                    OAuthProvider.GOOGLE, google("g-3", "same@gmail.com", true));
            User second = oauthLoginService.login(
                    OAuthProvider.GOOGLE, google("g-3", "same@gmail.com", true));

            assertThat(second.getId()).isEqualTo(first.getId());
            assertThat(userRepository.count()).isEqualTo(1);
        }

        @Test
        @DisplayName("이메일이 바뀌어도 제공자 식별자로 같은 사람을 찾는다")
        void 제공자_식별자로_찾는다() {
            User first = oauthLoginService.login(
                    OAuthProvider.GOOGLE, google("g-4", "before@gmail.com", true));
            User second = oauthLoginService.login(
                    OAuthProvider.GOOGLE, google("g-4", "after@gmail.com", true));

            assertThat(second.getId()).isEqualTo(first.getId());
        }
    }

    @Nested
    @DisplayName("이메일 규칙")
    class EmailRules {

        @Test
        @DisplayName("이메일이 없으면 가입시키지 않는다 — 카카오 선택 동의 대응")
        void 이메일이_없으면_거부() {
            OAuthAttributes noEmail = OAuthAttributes.of(OAuthProvider.KAKAO, Map.of(
                    "id", "k-1", "kakao_account", Map.of("profile", Map.of("nickname", "카카오"))));

            assertThatThrownBy(() -> oauthLoginService.login(OAuthProvider.KAKAO, noEmail))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(AuthErrorCode.OAUTH_EMAIL_REQUIRED);

            assertThat(userRepository.count()).isZero();
        }
    }

    @Nested
    @DisplayName("기존 계정 연결")
    class Linking {

        private User 일반가입(String email) {
            return userRepository.saveAndFlush(
                    User.create(email, passwordEncoder.encode("Passw0rd"), "기존사용자"));
        }

        @Test
        @DisplayName("구글이 이메일을 보증하면 기존 계정에 붙인다")
        void 검증된_구글은_연결한다() {
            User existing = 일반가입("dup@gmail.com");

            User user = oauthLoginService.login(
                    OAuthProvider.GOOGLE, google("g-5", "dup@gmail.com", true));

            assertThat(user.getId()).isEqualTo(existing.getId());
            assertThat(userRepository.count()).isEqualTo(1);
            assertThat(oauthAccountRepository.findAllByUserId(existing.getId())).hasSize(1);
        }

        @Test
        @DisplayName("email_verified 가 거짓이면 거부한다 — 이게 없으면 계정 탈취가 된다")
        void 미검증_이메일은_거부한다() {
            일반가입("victim@gmail.com");

            assertThatThrownBy(() -> oauthLoginService.login(
                    OAuthProvider.GOOGLE, google("attacker", "victim@gmail.com", false)))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(AuthErrorCode.OAUTH_EMAIL_ALREADY_REGISTERED);
        }

        @Test
        @DisplayName("카카오는 검증 여부와 무관하게 기존 계정에 붙이지 않는다")
        void 카카오는_연결하지_않는다() {
            일반가입("kakao-dup@otboo.com");
            OAuthAttributes attributes = OAuthAttributes.of(OAuthProvider.KAKAO, Map.of(
                    "id", "k-2",
                    "kakao_account", Map.of(
                            "email", "kakao-dup@otboo.com",
                            "is_email_verified", true,
                            "profile", Map.of("nickname", "카카오"))));

            assertThatThrownBy(() -> oauthLoginService.login(OAuthProvider.KAKAO, attributes))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(AuthErrorCode.OAUTH_EMAIL_ALREADY_REGISTERED);
        }
    }

    @Nested
    @DisplayName("응답")
    class Response {

        @Test
        @DisplayName("로그인 응답의 linkedOAuthProviders 에 연결된 제공자가 실린다")
        void 연결된_제공자를_알려준다() {
            User user = oauthLoginService.login(
                    OAuthProvider.GOOGLE, google("g-7", "linked@gmail.com", true));

            var result = authService.issueFor(user);

            assertThat(result.jwt().userDto().linkedOAuthProviders())
                    .containsExactly("google");
        }
    }

    @Nested
    @DisplayName("잠금")
    class Locked {

        @Test
        @DisplayName("잠긴 계정은 소셜로도 못 들어온다")
        void 잠긴_계정은_거부() {
            User user = oauthLoginService.login(
                    OAuthProvider.GOOGLE, google("g-6", "locked@gmail.com", true));
            user.changeLocked(true);
            userRepository.saveAndFlush(user);

            assertThatThrownBy(() -> oauthLoginService.login(
                    OAuthProvider.GOOGLE, google("g-6", "locked@gmail.com", true)))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(AuthErrorCode.ACCOUNT_LOCKED);
        }
    }
}
