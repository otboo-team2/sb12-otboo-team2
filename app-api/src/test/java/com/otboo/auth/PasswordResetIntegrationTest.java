package com.otboo.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.otboo.auth.password.PasswordResetMail;
import com.otboo.auth.password.PasswordResetMailer;
import com.otboo.common.test.IntegrationTestSupport;
import com.otboo.user.entity.OAuthProvider;
import com.otboo.user.entity.User;
import com.otboo.user.entity.UserOAuthAccount;
import com.otboo.user.repository.RefreshTokenRepository;
import com.otboo.user.repository.UserOAuthAccountRepository;
import com.otboo.user.repository.UserRepository;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 비밀번호 초기화 통합 테스트.
 *
 * <p>메일은 {@link MockitoBean} 으로 가로챈다 — 실제 SMTP 에 붙지 않으면서도
 * "무엇이 담겨 나갔는지"까지 확인할 수 있다.
 */
@AutoConfigureMockMvc
class PasswordResetIntegrationTest extends IntegrationTestSupport {

    private static final String EMAIL = "reset@otboo.com";
    private static final String PASSWORD = "Passw0rd!";

    /** {@code UserCreateRequest} 의 비밀번호 규칙. 임시 비밀번호도 이걸 만족해야 한다. */
    private static final Pattern PASSWORD_RULE =
            Pattern.compile("^(?=.*[A-Za-z])(?=.*\\d)[A-Za-z\\d@$!%*?&]{6,}$");

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired UserOAuthAccountRepository oauthAccountRepository;
    @Autowired RefreshTokenRepository refreshTokenRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired StringRedisTemplate redis;

    @MockitoBean PasswordResetMailer mailer;

    @BeforeEach
    void setUp() {
        refreshTokenRepository.deleteAll();
        oauthAccountRepository.deleteAll();
        userRepository.deleteAll();
        // 쿨다운이 테스트 사이에 넘어가면 뒤 테스트가 이유 없이 실패한다.
        Set<String> keys = redis.keys("password-reset:cooldown:*");
        if (keys != null && !keys.isEmpty()) {
            redis.delete(keys);
        }
    }

    private void requestReset(String email) throws Exception {
        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\"}".formatted(email))
                        .with(csrf()))
                .andExpect(status().isNoContent());
    }

    private PasswordResetMail capturedMail() {
        ArgumentCaptor<PasswordResetMail> captor = ArgumentCaptor.forClass(PasswordResetMail.class);
        verify(mailer).send(captor.capture());
        return captor.getValue();
    }

    /** 본문에서 4칸 들여쓴 줄이 임시 비밀번호다. */
    private static String temporaryPasswordIn(PasswordResetMail mail) {
        Matcher matcher = Pattern.compile("(?m)^ {4}(\\S+)$").matcher(mail.body());
        assertThat(matcher.find()).as("본문에 임시 비밀번호 줄이 있어야 한다").isTrue();
        return matcher.group(1);
    }

    @Test
    @DisplayName("가입된 계정 — 임시 비밀번호로 바뀌고 그 비밀번호로 로그인된다")
    void resetsPasswordAndMailsIt() throws Exception {
        userRepository.save(User.create(EMAIL, passwordEncoder.encode(PASSWORD), "여운정"));

        requestReset(EMAIL);

        String temporary = temporaryPasswordIn(capturedMail());
        assertThat(temporary).matches(PASSWORD_RULE);

        // 기존 비밀번호는 더 이상 통하지 않는다
        mockMvc.perform(multipart("/api/auth/sign-in")
                        .param("username", EMAIL).param("password", PASSWORD).with(csrf()))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(multipart("/api/auth/sign-in")
                        .param("username", EMAIL).param("password", temporary).with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("초기화하면 기존 세션(리프레시 토큰)이 모두 끊긴다")
    void revokesRefreshTokens() throws Exception {
        userRepository.save(User.create(EMAIL, passwordEncoder.encode(PASSWORD), "여운정"));
        mockMvc.perform(multipart("/api/auth/sign-in")
                        .param("username", EMAIL).param("password", PASSWORD).with(csrf()))
                .andExpect(status().isOk());
        assertThat(refreshTokenRepository.count()).isPositive();

        requestReset(EMAIL);

        assertThat(refreshTokenRepository.count()).isZero();
    }

    @Test
    @DisplayName("가입되지 않은 이메일 — 가입 여부를 알려주지 않으려고 똑같이 204 를 주고 메일은 안 보낸다")
    void hidesWhetherAccountExists() throws Exception {
        requestReset("nobody@otboo.com");

        verifyNoInteractions(mailer);
    }

    @Test
    @DisplayName("소셜 전용 계정 — 비밀번호를 만들지 않고 안내 메일만 보낸다")
    void doesNotCreatePasswordForSocialOnlyAccount() throws Exception {
        User user = userRepository.save(User.create(EMAIL, null, "여운정"));
        oauthAccountRepository.save(UserOAuthAccount.link(user, OAuthProvider.GOOGLE, "google-123"));

        requestReset(EMAIL);

        assertThat(capturedMail().body()).contains("구글");
        assertThat(userRepository.findByEmail(EMAIL).orElseThrow().hasPassword()).isFalse();
    }

    @Test
    @DisplayName("연달아 요청해도 쿨다운 안에서는 메일이 한 번만 나간다")
    void sendsOnlyOnceWithinCooldown() throws Exception {
        userRepository.save(User.create(EMAIL, passwordEncoder.encode(PASSWORD), "여운정"));

        requestReset(EMAIL);
        requestReset(EMAIL);

        verify(mailer, org.mockito.Mockito.times(1)).send(any(PasswordResetMail.class));
    }
}
