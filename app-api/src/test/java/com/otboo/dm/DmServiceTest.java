package com.otboo.dm;

import static org.assertj.core.api.Assertions.assertThat;

import com.otboo.common.pagination.CursorRequest;
import com.otboo.common.pagination.CursorResponse;
import com.otboo.common.pagination.SortDirection;
import com.otboo.common.test.IntegrationTestSupport;
import com.otboo.dm.dto.DirectMessageDto;
import com.otboo.dm.entity.DirectMessage;
import com.otboo.dm.repository.DirectMessageRepository;
import com.otboo.user.entity.User;
import com.otboo.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class DmServiceTest extends IntegrationTestSupport {

    @Autowired DmService dmService;
    @Autowired DirectMessageRepository directMessageRepository;
    @Autowired UserRepository userRepository;

    private User user1;
    private User user2;

    @BeforeEach
    void setUp() {
        directMessageRepository.deleteAll();
        userRepository.deleteAll();

        user1 = userRepository.save(User.createOAuth("user111@otboo.io", "사용자1"));
        user2 = userRepository.save(User.createOAuth("user222@otboo.io", "사용자2"));
    }

    private void send(User sender, User receiver, String content) {
        directMessageRepository.save(DirectMessage.create(sender, receiver, content));
    }

    @Nested
    @DisplayName("getMessages")
    class GetMessages {

        @Test
        @DisplayName("두 사람의 메시지를 최신순으로, 정렬 정보와 함께 돌려준다")
        void success() throws InterruptedException {
            send(user1, user2, "안녕");
            Thread.sleep(1);
            send(user2, user1, "ㅇ 안녕");

            CursorRequest request = new CursorRequest(null, null, 10, null, SortDirection.DESCENDING);
            CursorResponse<DirectMessageDto> response =
                dmService.getMessages(user1.getId(), user2.getId(), request);

            assertThat(response.data()).hasSize(2);
            assertThat(response.totalCount()).isEqualTo(2);
            assertThat(response.sortBy()).isEqualTo("createdAt");
            assertThat(response.sortDirection()).isEqualTo(SortDirection.DESCENDING);
            assertThat(response.data().get(0).content()).isEqualTo("ㅇ 안녕");
        }

        @Test
        @DisplayName("두 사람 순서를 바꿔 조회해도 같은 대화방이 나온다")
        void sameRoomRegardlessOfOrder() {
            send(user1, user2, "안녕");

            CursorRequest request = new CursorRequest(null, null, 10, null, SortDirection.DESCENDING);
            CursorResponse<DirectMessageDto> response =
                dmService.getMessages(user2.getId(), user1.getId(), request);

            assertThat(response.data()).hasSize(1);
        }
    }
}
