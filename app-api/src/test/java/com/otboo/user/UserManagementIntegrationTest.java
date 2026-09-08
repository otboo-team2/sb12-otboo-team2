package com.otboo.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.otboo.common.security.AuthPrincipal;
import com.otboo.common.test.IntegrationTestSupport;
import com.otboo.user.entity.Profile;
import com.otboo.user.entity.Role;
import com.otboo.user.entity.User;
import com.otboo.user.repository.ProfileRepository;
import com.otboo.user.repository.RefreshTokenRepository;
import com.otboo.user.repository.UserRepository;
import com.otboo.weather.repository.WeatherRegionRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

/** KAN-22 — 계정 목록 · 권한 · 잠금 · 프로필 · 비밀번호. */
@AutoConfigureMockMvc
class UserManagementIntegrationTest extends IntegrationTestSupport {

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired ProfileRepository profileRepository;
    @Autowired RefreshTokenRepository refreshTokenRepository;
    @Autowired WeatherRegionRepository weatherRegionRepository;
    @Autowired PasswordEncoder passwordEncoder;

    private User admin;
    private User member;

    @BeforeEach
    void setUp() {
        refreshTokenRepository.deleteAll();
        profileRepository.deleteAll();
        weatherRegionRepository.deleteAll();
        userRepository.deleteAll();

        admin = save("admin@otboo.com", "관리자", Role.ADMIN);
        member = save("member@otboo.com", "여운정", Role.USER);
    }

    private User save(String email, String name, Role role) {
        User user = userRepository.saveAndFlush(
                User.create(email, passwordEncoder.encode("Passw0rd"), name));
        if (role == Role.ADMIN) {
            user.changeRole(Role.ADMIN);
            userRepository.saveAndFlush(user);
        }
        profileRepository.saveAndFlush(Profile.createEmpty(user));
        return user;
    }

    /** 토큰 발급 대신 인증 주체를 직접 넣는다. 이 테스트가 검증하는 것은 인가지 로그인이 아니다. */
    private static <T extends MockHttpServletRequestBuilder> T as(T builder, User user) {
        builder.with(csrf());
        builder.with(authentication(new UsernamePasswordAuthenticationToken(
                new AuthPrincipal(user.getId(), user.getEmail(), user.getRole()),
                null,
                List.of(new SimpleGrantedAuthority(user.getRole().authority())))));
        return builder;
    }

    @Nested
    @DisplayName("계정 목록 (관리자)")
    class FindAll {

        @Test
        @DisplayName("커서 응답 형식을 그대로 돌려준다")
        void success() throws Exception {
            mockMvc.perform(as(get("/api/users")
                            .param("limit", "10")
                            .param("sortBy", "createdAt")
                            .param("sortDirection", "DESCENDING"), admin))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data.length()").value(2))
                    .andExpect(jsonPath("$.totalCount").value(2))
                    .andExpect(jsonPath("$.hasNext").value(false))
                    .andExpect(jsonPath("$.sortBy").value("createdAt"))
                    .andExpect(jsonPath("$.sortDirection").value("DESCENDING"));
        }

        @Test
        @DisplayName("일반 사용자는 403")
        void memberForbidden() throws Exception {
            mockMvc.perform(as(get("/api/users").param("limit", "10"), member))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("이메일 필터가 적용되고 totalCount 도 필터 기준이다")
        void filterByEmail() throws Exception {
            mockMvc.perform(as(get("/api/users")
                            .param("limit", "10")
                            .param("emailLike", "admin"), admin))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(1))
                    .andExpect(jsonPath("$.data[0].email").value("admin@otboo.com"))
                    .andExpect(jsonPath("$.totalCount").value(1));
        }

        @Test
        @DisplayName("한 페이지를 넘기면 hasNext 와 다음 커서가 채워지고, 두 번째 페이지가 이어진다")
        void paginates() throws Exception {
            save("a@otboo.com", "가나", Role.USER);
            save("b@otboo.com", "다라", Role.USER);

            String cursor = mockMvc.perform(as(get("/api/users")
                            .param("limit", "2")
                            .param("sortBy", "email")
                            .param("sortDirection", "ASCENDING"), admin))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.hasNext").value(true))
                    .andExpect(jsonPath("$.totalCount").value(4))
                    .andExpect(jsonPath("$.data[0].email").value("a@otboo.com"))
                    .andExpect(jsonPath("$.data[1].email").value("admin@otboo.com"))
                    .andExpect(jsonPath("$.nextCursor").value("admin@otboo.com"))
                    .andReturn().getResponse().getContentAsString();

            String nextIdAfter = cursor.replaceAll(".*\"nextIdAfter\":\"([^\"]+)\".*", "$1");

            mockMvc.perform(as(get("/api/users")
                            .param("limit", "2")
                            .param("sortBy", "email")
                            .param("sortDirection", "ASCENDING")
                            .param("cursor", "admin@otboo.com")
                            .param("idAfter", nextIdAfter), admin))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].email").value("b@otboo.com"))
                    .andExpect(jsonPath("$.data[1].email").value("member@otboo.com"))
                    .andExpect(jsonPath("$.hasNext").value(false));
        }

        @Test
        @DisplayName("허용되지 않은 정렬 기준은 400")
        void rejectsUnknownSortBy() throws Exception {
            mockMvc.perform(as(get("/api/users")
                            .param("limit", "10")
                            .param("sortBy", "password"), admin))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.exceptionName").value("USER_004"));
        }
    }

    @Nested
    @DisplayName("권한 · 잠금 (관리자)")
    class RoleAndLock {

        @Test
        @DisplayName("권한을 바꾸면 리프레시 토큰이 사라진다 — 토큰에 role 이 들어 있다")
        void changeRole() throws Exception {
            mockMvc.perform(as(patch("/api/users/{id}/role", member.getId()), admin)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"role\":\"ADMIN\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.role").value("ADMIN"));

            assertThat(userRepository.findById(member.getId()).orElseThrow().getRole())
                    .isEqualTo(Role.ADMIN);
            assertThat(refreshTokenRepository.count()).isZero();
        }

        @Test
        @DisplayName("본인 권한은 내릴 수 없다 — 관리자가 스스로 잠기는 것을 막는다")
        void cannotDemoteSelf() throws Exception {
            mockMvc.perform(as(patch("/api/users/{id}/role", admin.getId()), admin)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"role\":\"USER\"}"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.exceptionName").value("USER_102"));
        }

        @Test
        @DisplayName("계정을 잠근다")
        void lock() throws Exception {
            mockMvc.perform(as(patch("/api/users/{id}/lock", member.getId()), admin)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"locked\":true}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.locked").value(true));
        }

        @Test
        @DisplayName("본인 계정은 잠글 수 없다")
        void cannotLockSelf() throws Exception {
            mockMvc.perform(as(patch("/api/users/{id}/lock", admin.getId()), admin)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"locked\":true}"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.exceptionName").value("USER_103"));
        }

        @Test
        @DisplayName("locked 를 빼먹으면 400 — 조용히 false 가 되면 안 된다")
        void requiresLockedField() throws Exception {
            mockMvc.perform(as(patch("/api/users/{id}/lock", member.getId()), admin)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("없는 사용자는 404")
        void notFound() throws Exception {
            mockMvc.perform(as(patch("/api/users/{id}/role",
                            java.util.UUID.randomUUID()), admin)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"role\":\"ADMIN\"}"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.exceptionName").value("USER_200"));
        }
    }

    @Nested
    @DisplayName("프로필")
    class Profiles {

        private MockMultipartHttpServletRequestBuilder patchProfile(User target) {
            MockMultipartHttpServletRequestBuilder builder =
                    (MockMultipartHttpServletRequestBuilder) multipart("/api/users/{id}/profiles",
                            target.getId()).with(request -> {
                        request.setMethod("PATCH");
                        return request;
                    });
            return builder;
        }

        @Test
        @DisplayName("남의 프로필도 조회할 수 있다")
        void findOther() throws Exception {
            mockMvc.perform(as(get("/api/users/{id}/profiles", admin.getId()), member))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.userId").value(admin.getId().toString()))
                    .andExpect(jsonPath("$.name").value("관리자"));
        }

        @Test
        @DisplayName("위치를 보내면 격자 지역이 만들어지고 지명이 응답에 실린다")
        void updateWithLocation() throws Exception {
            String request = """
                    {"name":"운정","gender":"FEMALE","temperatureSensitivity":4,
                     "location":{"latitude":37.5,"longitude":127.0,"x":60,"y":127,
                                 "locationNames":["서울특별시","강남구"]}}
                    """;

            mockMvc.perform(as(patchProfile(member), member)
                            .file(new MockMultipartFile("request", "", "application/json",
                                    request.getBytes())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("운정"))
                    .andExpect(jsonPath("$.gender").value("FEMALE"))
                    .andExpect(jsonPath("$.temperatureSensitivity").value(4))
                    .andExpect(jsonPath("$.location.x").value(60))
                    .andExpect(jsonPath("$.location.locationNames[1]").value("강남구"));

            assertThat(weatherRegionRepository.findByGridXAndGridY(60, 127)).isPresent();
            // 이름은 계정에 저장된다. 프로필에 복사해두지 않는다.
            assertThat(userRepository.findById(member.getId()).orElseThrow().getName())
                    .isEqualTo("운정");
        }

        @Test
        @DisplayName("지명 없는 위치는 무시한다 — 지역을 만들 수 없어 400 이 나가면 안 된다")
        void ignoresLocationWithoutNames() throws Exception {
            String request = """
                    {"location":{"latitude":37.5,"longitude":127.0,"x":60,"y":127,
                                 "locationNames":[]}}
                    """;

            mockMvc.perform(as(patchProfile(member), member)
                            .file(new MockMultipartFile("request", "", "application/json",
                                    request.getBytes())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.location").doesNotExist());

            assertThat(weatherRegionRepository.findByGridXAndGridY(60, 127)).isEmpty();
        }

        @Test
        @DisplayName("보내지 않은 항목은 지워지지 않는다 — PATCH 다")
        void partialUpdateKeepsOtherFields() throws Exception {
            mockMvc.perform(as(patchProfile(member), member)
                            .file(new MockMultipartFile("request", "", "application/json",
                                    "{\"gender\":\"MALE\",\"temperatureSensitivity\":2}".getBytes())))
                    .andExpect(status().isOk());

            mockMvc.perform(as(patchProfile(member), member)
                            .file(new MockMultipartFile("request", "", "application/json",
                                    "{\"temperatureSensitivity\":5}".getBytes())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.gender").value("MALE"))
                    .andExpect(jsonPath("$.temperatureSensitivity").value(5));
        }

        @Test
        @DisplayName("남의 프로필은 수정할 수 없다 — 경로의 userId 를 믿지 않는다")
        void cannotUpdateOthers() throws Exception {
            mockMvc.perform(as(patchProfile(admin), member)
                            .file(new MockMultipartFile("request", "", "application/json",
                                    "{\"gender\":\"MALE\"}".getBytes())))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.exceptionName").value("USER_101"));
        }

        @Test
        @DisplayName("온도 민감도가 범위를 벗어나면 400")
        void rejectsOutOfRangeSensitivity() throws Exception {
            mockMvc.perform(as(patchProfile(member), member)
                            .file(new MockMultipartFile("request", "", "application/json",
                                    "{\"temperatureSensitivity\":9}".getBytes())))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("이미지가 아닌 파일은 400")
        void rejectsNonImage() throws Exception {
            mockMvc.perform(as(patchProfile(member), member)
                            .file(new MockMultipartFile("request", "", "application/json",
                                    "{}".getBytes()))
                            .file(new MockMultipartFile("image", "bad.exe",
                                    "application/octet-stream", "MZ".getBytes())))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.exceptionName").value("COMMON_007"));
        }
    }

    @Nested
    @DisplayName("비밀번호 변경")
    class ChangePassword {

        @Test
        @DisplayName("바꾸면 인코딩되어 저장되고 기존 세션은 끊긴다")
        void success() throws Exception {
            mockMvc.perform(as(patch("/api/users/{id}/password", member.getId()), member)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"password\":\"NewPass1\"}"))
                    .andExpect(status().isNoContent());

            String stored = userRepository.findById(member.getId()).orElseThrow().getPassword();
            assertThat(stored).isNotEqualTo("NewPass1");
            assertThat(passwordEncoder.matches("NewPass1", stored)).isTrue();
            assertThat(refreshTokenRepository.count()).isZero();
        }

        @Test
        @DisplayName("남의 비밀번호는 바꿀 수 없다 — 관리자도 마찬가지다")
        void cannotChangeOthers() throws Exception {
            mockMvc.perform(as(patch("/api/users/{id}/password", member.getId()), admin)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"password\":\"NewPass1\"}"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.exceptionName").value("USER_101"));
        }

        @Test
        @DisplayName("가입과 같은 규칙을 적용한다 — 숫자 없는 비밀번호는 400")
        void sameRuleAsSignUp() throws Exception {
            mockMvc.perform(as(patch("/api/users/{id}/password", member.getId()), member)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"password\":\"onlyletters\"}"))
                    .andExpect(status().isBadRequest());
        }
    }
}
