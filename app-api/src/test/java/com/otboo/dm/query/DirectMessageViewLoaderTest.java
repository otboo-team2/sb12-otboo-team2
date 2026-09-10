package com.otboo.dm.query;

import static org.assertj.core.api.Assertions.assertThat;

import com.otboo.common.pagination.CursorRequest;
import com.otboo.common.pagination.SortDirection;
import com.otboo.common.test.IntegrationTestSupport;
import com.otboo.dm.dto.DirectMessageDto;
import com.otboo.dm.entity.DirectMessage;
import com.otboo.dm.repository.DirectMessageRepository;
import com.otboo.dm.util.DmKeyGenerator;
import com.otboo.user.entity.User;
import com.otboo.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class DirectMessageViewLoaderTest extends IntegrationTestSupport {

    @Autowired DirectMessageViewLoader viewLoader;
    @Autowired DirectMessageRepository directMessageRepository;
    @Autowired UserRepository userRepository;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired EntityManager entityManager;

    private User user1;
    private User user2;
    private User other;
    private String dmKey;

    @BeforeEach
    void setUp() {
        directMessageRepository.deleteAll();
        userRepository.deleteAll();
        entityManager.flush();

        user1 = userRepository.save(User.createOAuth("user111@otboo.io", "사용자1"));
        user2 = userRepository.save(User.createOAuth("user222@otboo.io", "사용자2"));
        other = userRepository.save(User.createOAuth("other@otboo.io", "누구세요"));
        dmKey = DmKeyGenerator.generate(user1.getId(), user2.getId());
    }

    private DirectMessage save(User sender, User receiver, String content) {
        return directMessageRepository.saveAndFlush(DirectMessage.create(sender, receiver, content));
    }

    private CursorRequest firstPage(int limit) {
        return new CursorRequest(null, null, limit, null, SortDirection.DESCENDING);
    }

    @Nested
    @DisplayName("loadSlice")
    class LoadSlice {

        @Test
        @DisplayName("다른 대화방 메시지는 섞이지 않는다")
        void filtersByDmKey() {
            save(user1, user2, "안녕하세요");
            save(user1, other, "다른 방 메시지");

            List<DirectMessageDto> result = viewLoader.loadSlice(dmKey, firstPage(10));

            assertThat(result).hasSize(1);
            assertThat(result.getFirst().content()).isEqualTo("안녕하세요");
        }

        @Test
        @DisplayName("커서 없이 첫 페이지를 요청하면 최신순으로 온다")
        void firstPageOrder() throws InterruptedException {
            DirectMessage m1 = save(user1, user2, "첫번째");
            Thread.sleep(1);
            DirectMessage m2 = save(user2, user1, "두번째");

            List<DirectMessageDto> result = viewLoader.loadSlice(dmKey, firstPage(10));

            assertThat(result).extracting(DirectMessageDto::id)
                .containsExactly(m2.getId(), m1.getId());
        }

        @Test
        @DisplayName("cursor + idAfter를 넘기면 그 이전 메시지만 온다")
        void nextPageWithIdAfter() throws InterruptedException {
            DirectMessage m1 = save(user1, user2, "1");
            Thread.sleep(1);
            save(user1, user2, "2");
            Thread.sleep(1);
            save(user1, user2, "3");

            List<DirectMessageDto> firstPage = viewLoader.loadSlice(dmKey, firstPage(1));
            DirectMessageDto last = firstPage.getLast();

            CursorRequest nextPage = new CursorRequest(
                last.createdAt().toString(), last.id(), 10, null, SortDirection.DESCENDING);
            List<DirectMessageDto> result = viewLoader.loadSlice(dmKey, nextPage);

            assertThat(result).extracting(DirectMessageDto::id).containsExactly(m1.getId());
        }

        @Test
        @DisplayName("idAfter 없이 cursor만 오면 같은 시각 데이터를 포기하고 시각만으로 비교한다")
        void nextPageWithoutIdAfter() throws InterruptedException {
            DirectMessage m1 = save(user1, user2, "1");
            Thread.sleep(1);
            DirectMessage m2 = save(user1, user2, "2");

            CursorRequest cursorOnly = new CursorRequest(
                m2.getCreatedAt().toString(), null, 10, null, SortDirection.DESCENDING);
            List<DirectMessageDto> result = viewLoader.loadSlice(dmKey, cursorOnly);

            assertThat(result).extracting(DirectMessageDto::id).containsExactly(m1.getId());
        }

        @Test
        @DisplayName("createdAt이 같으면 id 내림차순으로 동점 처리한다")
        void tieBreaksById() {
            DirectMessage m1 = save(user1, user2, "1");
            DirectMessage m2 = save(user1, user2, "2");

            entityManager.flush();
            jdbcTemplate.update(
                "update direct_messages set created_at = ? where id in (?, ?)",
                Timestamp.from(m1.getCreatedAt()),
                m1.getId().toString(),
                m2.getId().toString());
            entityManager.clear();

            List<DirectMessageDto> result = viewLoader.loadSlice(dmKey, firstPage(10));

            String expectedFirst = m1.getId().toString().compareTo(m2.getId().toString()) > 0
                ? m1.getId().toString() : m2.getId().toString();

            assertThat(result.getFirst().id().toString()).isEqualTo(expectedFirst);
        }

        @Test
        @DisplayName("발신자·수신자 이름이 함께 채워진다")
        void includesUserSummaries() {
            save(user1, user2, "안녕");

            DirectMessageDto dto = viewLoader.loadSlice(dmKey, firstPage(10)).getFirst();

            assertThat(dto.sender().name()).isEqualTo("사용자1");
            assertThat(dto.receiver().name()).isEqualTo("사용자2");
        }
    }
}
