package com.otboo.notification.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.otboo.common.test.IntegrationTestSupport;
import com.otboo.notification.entity.Notification;
import com.otboo.notification.entity.NotificationLevel;
import com.otboo.notification.entity.NotificationType;
import com.otboo.user.entity.User;
import com.otboo.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class NotificationRepositoryTest extends IntegrationTestSupport {

    @Autowired NotificationRepository notificationRepository;
    @Autowired UserRepository userRepository;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired EntityManager entityManager;

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

    private Notification save(User receiver) {
        return notificationRepository.save(
            Notification.create(
                receiver, actor,
                NotificationType.FOLLOW_CREATED, UUID.randomUUID().toString(),
            "제목", "내용",
                NotificationLevel.INFO
            )
        );
    }

    @Nested
    @DisplayName("findByReceiverId")
    class FindByReceiverId {

        @Test
        @DisplayName("다른 유저 알림은 섞이지 않는다")
        void filtersByReceiver() {
            save(user1);
            save(user2);

            List<Notification> result = notificationRepository.findByReceiverId(
                user1.getId(), null, null, PageRequest.of(0, 10));

            assertThat(result).hasSize(1);
            assertThat(result.getFirst().getReceiver().getId()).isEqualTo(user1.getId());
        }

        @Test
        @DisplayName("커서 없이 첫 페이지를 요청하면 최신순으로 온다")
        void firstPage() throws InterruptedException {
            Notification n1 = save(user1);
            Thread.sleep(1);
            Notification n2 = save(user1);

            List<Notification> result = notificationRepository.findByReceiverId(
                user1.getId(), null, null, PageRequest.of(0, 10));

            assertThat(result).extracting(Notification::getId)
                .containsExactly(n2.getId(), n1.getId());
        }

        @Test
        @DisplayName("커서를 넘기면 그 이전 알림만 온다")
        void nextPage() throws InterruptedException {
            Notification n1 = save(user1);
            Thread.sleep(1);
            Notification n2 = save(user1);
            Thread.sleep(1);
            Notification n3 = save(user1);

            List<Notification> firstPage = notificationRepository.findByReceiverId(
                user1.getId(), null, null, PageRequest.of(0, 2));
            Notification last = firstPage.getLast(); // n2

            List<Notification> secondPage = notificationRepository.findByReceiverId(
                user1.getId(), last.getCreatedAt(), last.getId(), PageRequest.of(0, 10));

            assertThat(secondPage).extracting(Notification::getId).containsExactly(n1.getId());
        }

        @Test
        @DisplayName("createdAt이 같으면 id 문자열 내림차순으로 동점 처리한다")
        void tieBreaksById() {
            Notification n1 = save(user1);
            Notification n2 = save(user1);

            entityManager.flush();

            jdbcTemplate.update(
                "update notifications set created_at = ? where id in (?, ?)",
                Timestamp.from(n1.getCreatedAt()),
                n1.getId().toString(),
                n2.getId().toString()
            );

            entityManager.clear();

            List<Notification> result = notificationRepository.findByReceiverId(
                user1.getId(),
                null,
                null,
                PageRequest.of(0, 10)
            );

            UUID expectedFirst = n1.getId().toString().compareTo(n2.getId().toString()) > 0
                ? n1.getId()
                : n2.getId();

            UUID expectedSecond = n1.getId().toString().compareTo(n2.getId().toString()) > 0
                ? n2.getId()
                : n1.getId();

            assertThat(result)
                .extracting(Notification::getId)
                .containsExactly(expectedFirst, expectedSecond);
        }
    }

    @Nested
    @DisplayName("countByReceiverId")
    class CountByReceiverId {

        @Test
        @DisplayName("해당 유저 알림 개수만 센다")
        void countsOnlyReceiverNotifications() {
            save(user1);
            save(user1);
            save(user2);

            assertThat(notificationRepository.countByReceiverId(user1.getId())).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("deleteByIdAndReceiverId")
    class DeleteByIdAndReceiverId {

        @Test
        @DisplayName("본인 알림이면 삭제된다")
        void deletesOwnNotification() {
            Notification notification = save(user1);

            int deleted = notificationRepository.deleteByIdAndReceiverId(
                notification.getId(), user1.getId());

            assertThat(deleted).isEqualTo(1);
            assertThat(notificationRepository.findById(notification.getId())).isEmpty();
        }

        @Test
        @DisplayName("다른 사람 알림은 지울 수 없다")
        void cannotDeleteOthersNotification() {
            Notification notification = save(user1);

            int deleted = notificationRepository.deleteByIdAndReceiverId(
                notification.getId(), user2.getId());

            assertThat(deleted).isEqualTo(0);
            assertThat(notificationRepository.findById(notification.getId())).isPresent();
        }
    }
}
