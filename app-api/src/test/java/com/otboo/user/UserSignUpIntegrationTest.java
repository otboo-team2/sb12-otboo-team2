package com.otboo.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.otboo.common.test.IntegrationTestSupport;
import com.otboo.user.entity.User;
import com.otboo.user.repository.RefreshTokenRepository;
import com.otboo.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class UserSignUpIntegrationTest extends IntegrationTestSupport {

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired RefreshTokenRepository refreshTokenRepository;
    @Autowired PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    private org.springframework.test.web.servlet.ResultActions signUp(String body) throws Exception {
        return mockMvc.perform(post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .with(csrf()));
    }

    private static String body(String name, String email, String password) {
        return """
                {"name":"%s","email":"%s","password":"%s"}
                """.formatted(name, email, password);
    }

    @Nested
    @DisplayName("가입")
    class Create {

        @Test
        @DisplayName("가입하면 201 과 계정 정보를 돌려준다")
        void success() throws Exception {
            signUp(body("여운정", "me@otboo.com", "Passw0rd"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").isNotEmpty())
                    .andExpect(jsonPath("$.email").value("me@otboo.com"))
                    .andExpect(jsonPath("$.name").value("여운정"))
                    .andExpect(jsonPath("$.role").value("USER"))
                    .andExpect(jsonPath("$.locked").value(false))
                    .andExpect(jsonPath("$.linkedOAuthProviders").isArray());
        }

        @Test
        @DisplayName("응답에 비밀번호가 실리지 않는다")
        void neverExposesPassword() throws Exception {
            String response = signUp(body("여운정", "me@otboo.com", "Passw0rd"))
                    .andReturn().getResponse().getContentAsString();

            assertThat(response).doesNotContain("Passw0rd").doesNotContain("password");
        }

        @Test
        @DisplayName("비밀번호는 해시로 저장된다")
        void storesHashedPassword() throws Exception {
            signUp(body("여운정", "me@otboo.com", "Passw0rd")).andExpect(status().isCreated());

            User saved = userRepository.findByEmail("me@otboo.com").orElseThrow();
            assertThat(saved.getPassword()).isNotEqualTo("Passw0rd");
            assertThat(passwordEncoder.matches("Passw0rd", saved.getPassword())).isTrue();
        }

        @Test
        @DisplayName("가입 직후 그 계정으로 로그인된다")
        void canSignInAfterSignUp() throws Exception {
            signUp(body("여운정", "me@otboo.com", "Passw0rd")).andExpect(status().isCreated());

            mockMvc.perform(multipart("/api/auth/sign-in")
                            .param("username", "me@otboo.com")
                            .param("password", "Passw0rd")
                            .with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").isNotEmpty());
        }
    }

    @Nested
    @DisplayName("이메일 중복")
    class Duplicate {

        @Test
        @DisplayName("같은 이메일로 두 번 가입할 수 없다")
        void rejectsDuplicate() throws Exception {
            signUp(body("여운정", "me@otboo.com", "Passw0rd")).andExpect(status().isCreated());

            signUp(body("다른사람", "me@otboo.com", "Passw0rd"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.exceptionName").value("USER_300"));
        }

        @Test
        @DisplayName("대소문자만 다른 이메일도 같은 계정으로 본다")
        void treatsCaseInsensitively() throws Exception {
            signUp(body("여운정", "me@otboo.com", "Passw0rd")).andExpect(status().isCreated());

            signUp(body("다른사람", "ME@Otboo.com", "Passw0rd"))
                    .andExpect(status().isConflict());

            assertThat(userRepository.count()).isEqualTo(1);
        }

        @Test
        @DisplayName("이메일은 소문자로 정규화해 저장한다")
        void normalizesEmail() throws Exception {
            signUp(body("여운정", "  ME@Otboo.com  ", "Passw0rd")).andExpect(status().isCreated());

            assertThat(userRepository.findByEmail("me@otboo.com")).isPresent();
        }
    }

    @Nested
    @DisplayName("입력 검증 — 프론트를 거치지 않는 요청도 막아야 한다")
    class Validation {

        @Test
        @DisplayName("이메일 형식이 틀리면 어떤 필드가 왜 틀렸는지 알려준다")
        void invalidEmail() throws Exception {
            signUp(body("여운정", "not-an-email", "Passw0rd"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.exceptionName").value("COMMON_001"))
                    .andExpect(jsonPath("$.details.email").value("유효하지 않은 이메일입니다."));
        }

        @Test
        @DisplayName("비밀번호에 숫자가 없으면 거절한다")
        void passwordWithoutDigit() throws Exception {
            signUp(body("여운정", "me@otboo.com", "abcdefg"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.details.password").isNotEmpty());
        }

        @Test
        @DisplayName("비밀번호가 6자 미만이면 거절한다")
        void passwordTooShort() throws Exception {
            signUp(body("여운정", "me@otboo.com", "ab12"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.details.password").isNotEmpty());
        }

        @Test
        @DisplayName("이름에 허용되지 않은 문자가 있으면 거절한다")
        void invalidName() throws Exception {
            signUp(body("여운정!!", "me@otboo.com", "Passw0rd"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.details.name").isNotEmpty());
        }

        @Test
        @DisplayName("여러 필드가 틀리면 한 번에 모두 알려준다")
        void reportsAllFieldsAtOnce() throws Exception {
            signUp(body("!", "bad", "x"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.details.name").isNotEmpty())
                    .andExpect(jsonPath("$.details.email").isNotEmpty())
                    .andExpect(jsonPath("$.details.password").isNotEmpty());
        }
    }
}
