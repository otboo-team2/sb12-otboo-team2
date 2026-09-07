package com.otboo.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.otboo.common.test.IntegrationTestSupport;
import com.otboo.notification.entity.Notification;
import com.otboo.notification.entity.NotificationLevel;
import com.otboo.notification.entity.NotificationType;
import com.otboo.notification.repository.NotificationRepository;
import com.otboo.user.entity.User;
import com.otboo.user.repository.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class NotificationControllerTest extends IntegrationTestSupport {

    private static final String PASSWORD = "password";

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired NotificationRepository notificationRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired ObjectMapper objectMapper;

    private User user1;
    private User user2;
    private String accessToken;

    @BeforeEach
    void setUp() throws Exception {
        notificationRepository.deleteAll();
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

    private Notification save(User receiver) {
        return notificationRepository.save(Notification.create(
            receiver, user2, NotificationType.FOLLOW_CREATED, UUID.randomUUID().toString(),
            "제목", "내용", NotificationLevel.INFO));
    }

    @Nested
    @DisplayName("알림 목록 조회")
    class GetNotifications {

        @Test
        @DisplayName("본인 알림만 조회된다")
        void success() throws Exception {
            save(user1);
            save(user2);

            mockMvc.perform(get("/api/notifications")
                    .header("Authorization", "Bearer " + accessToken)
                    .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].receiverId").value(user1.getId().toString()));
        }

        @Test
        @DisplayName("토큰 없이 요청 시 401")
        void unauthenticated() throws Exception {
            mockMvc.perform(get("/api/notifications").param("limit", "10"))
                .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("알림 삭제(읽음 처리)")
    class DeleteNotification {

        @Test
        @DisplayName("본인 알림을 삭제한다")
        void success() throws Exception {
            Notification notification = save(user1);

            mockMvc.perform(delete("/api/notifications/" + notification.getId())
                    .header("Authorization", "Bearer " + accessToken)
                    .with(csrf()))
                .andExpect(status().isNoContent());

            assertThat(notificationRepository.findById(notification.getId())).isEmpty();
        }

        @Test
        @DisplayName("다른 사람 알림은 삭제 요청을 보내도 실제로는 지워지지 않는다")
        void cannotDeleteOthers() throws Exception {
            Notification notification = save(user2);

            mockMvc.perform(delete("/api/notifications/" + notification.getId())
                    .header("Authorization", "Bearer " + accessToken)
                    .with(csrf()))
                .andExpect(status().isNotFound());

            assertThat(notificationRepository.findById(notification.getId())).isPresent();
        }

        @Test
        @DisplayName("토큰 없이 요청하면 401")
        void unauthenticated() throws Exception {
            mockMvc.perform(delete("/api/notifications/" + UUID.randomUUID()).with(csrf()))
                .andExpect(status().isUnauthorized());
        }
    }
}
