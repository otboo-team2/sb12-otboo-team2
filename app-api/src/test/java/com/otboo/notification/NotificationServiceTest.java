package com.otboo.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.otboo.common.exception.BusinessException;
import com.otboo.common.pagination.CursorRequest;
import com.otboo.common.pagination.CursorResponse;
import com.otboo.common.pagination.SortDirection;
import com.otboo.common.test.IntegrationTestSupport;
import com.otboo.notification.dto.NotificationDto;
import com.otboo.notification.entity.NotificationLevel;
import com.otboo.notification.entity.NotificationType;
import com.otboo.notification.exception.NotificationErrorCode;
import com.otboo.notification.repository.NotificationRepository;
import com.otboo.user.entity.User;
import com.otboo.user.repository.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class NotificationServiceTest extends IntegrationTestSupport {

    @Autowired NotificationService notificationService;
    @Autowired NotificationRepository notificationRepository;
    @Autowired UserRepository userRepository;

    private User user1;
    private User user2;
    private User actor;

    @BeforeEach
    void setUp() {
        notificationRepository.deleteAll();
        userRepository.deleteAll();
        user1 = userRepository.save(User.createOAuth("user111@otboo.io", "사용자1"));
        user2 = userRepository.save(User.createOAuth("user222@otboo.io", "사용자2"));
        actor = userRepository.save(User.createOAuth("actor@otboo.io", "보낸사람"));
    }

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("알림을 저장한다")
        void success() {
            notificationService.create(
                user1, actor,
                NotificationType.FOLLOW_CREATED, UUID.randomUUID().toString(),
                "제목", "내용",
                NotificationLevel.INFO
            );

            assertThat(notificationRepository.countByReceiverId(user1.getId())).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("getNotifications")
    class GetNotifications {

        @Test
        @DisplayName("본인 알림만, 최신순으로 DTO에 담겨 온다")
        void success() throws InterruptedException {
            notificationService.create(
                user1, actor,
                NotificationType.FOLLOW_CREATED, UUID.randomUUID().toString(),
                "제목1", "내용1",
                NotificationLevel.INFO
            );
            Thread.sleep(1);
            notificationService.create(
                user1, actor,
                NotificationType.FOLLOW_CREATED, UUID.randomUUID().toString(),
                "제목2", "내용2",
                NotificationLevel.INFO
            );
            notificationService.create(
                user2, actor,
                NotificationType.FOLLOW_CREATED, UUID.randomUUID().toString(),
                "남의 알림", "내용",
                NotificationLevel.INFO
            );

            CursorRequest request = new CursorRequest(null, null, 10, null, SortDirection.DESCENDING);
            CursorResponse<NotificationDto> response =
                notificationService.getNotifications(user1.getId(), request);

            assertThat(response.data()).hasSize(2);
            assertThat(response.totalCount()).isEqualTo(2);
            assertThat(response.data().get(0).title()).isEqualTo("제목2");
            assertThat(response.data().get(1).title()).isEqualTo("제목1");
        }
    }

    @Nested
    @DisplayName("delete")
    class Delete {

        @Test
        @DisplayName("본인 알림이면 삭제된다")
        void success() {
            notificationService.create(
                user1, actor,
                NotificationType.FOLLOW_CREATED, UUID.randomUUID().toString(),
                "제목", "내용",
                NotificationLevel.INFO
            );
            UUID notificationId = notificationRepository.findAll().getFirst().getId();

            notificationService.delete(notificationId, user1.getId());

            assertThat(notificationRepository.findById(notificationId)).isEmpty();
        }

        @Test
        @DisplayName("존재하지 않는 알림이면 NOT_FOUND 예외를 던진다")
        void notFound() {
            assertThatThrownBy(() -> notificationService.delete(UUID.randomUUID(), user1.getId()))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(NotificationErrorCode.NOT_FOUND);
        }
    }
}
