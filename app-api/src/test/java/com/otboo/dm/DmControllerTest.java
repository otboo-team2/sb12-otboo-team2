package com.otboo.dm;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.otboo.common.test.IntegrationTestSupport;
import com.otboo.dm.entity.DirectMessage;
import com.otboo.dm.repository.DirectMessageRepository;
import com.otboo.user.entity.User;
import com.otboo.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class DmControllerTest extends IntegrationTestSupport {

    private static final String PASSWORD = "password";

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired DirectMessageRepository directMessageRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired ObjectMapper objectMapper;

    private User user1;
    private User user2;
    private String accessToken;

    @BeforeEach
    void setUp() throws Exception {
        directMessageRepository.deleteAll();
        userRepository.deleteAll();

        user1 = userRepository.save(User.create("user111@otboo.io", passwordEncoder.encode(PASSWORD), "사용자1"));
        user2 = userRepository.save(User.create("user222@otboo.io", passwordEncoder.encode(PASSWORD), "사용자2"));

        var result = mockMvc.perform(multipart("/api/auth/sign-in")
                .param("username", "user111@otboo.io")
                .param("password", PASSWORD)
                .with(csrf()))
            .andExpect(status().isOk())
            .andReturn();

        accessToken = objectMapper.readTree(result.getResponse().getContentAsString())
            .get("accessToken").asText();
    }

    @Nested
    @DisplayName("DM 목록 조회")
    class GetDms {

        @Test
        @DisplayName("두 사람의 대화 목록이 조회된다")
        void success() throws Exception {
            directMessageRepository.save(DirectMessage.create(user1, user2, "안녕"));

            mockMvc.perform(get("/api/direct-messages")
                    .header("Authorization", "Bearer " + accessToken)
                    .param("userId", user2.getId().toString())
                    .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].content").value("안녕"))
                .andExpect(jsonPath("$.data[0].sender.name").value("사용자1"));
        }

        @Test
        @DisplayName("토큰 없이 요청하면 401")
        void unauthenticated() throws Exception {
            mockMvc.perform(get("/api/direct-messages")
                    .param("userId", user2.getId().toString())
                    .param("limit", "10"))
                .andExpect(status().isUnauthorized());
        }
    }
}
