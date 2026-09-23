package com.otboo.notification.listener;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.otboo.common.event.ClothesAttributeAddedEvent;
import com.otboo.common.event.DirectMessageReceivedEvent;
import com.otboo.common.event.FeedCommentedEvent;
import com.otboo.common.event.FeedLikedEvent;
import com.otboo.common.event.FollowCreatedEvent;
import com.otboo.common.event.FollowedUserPostedEvent;
import com.otboo.common.event.UserRoleChangedEvent;
import com.otboo.common.event.VirtualTryOnCompletedEvent;
import com.otboo.common.exception.BusinessException;
import com.otboo.feed.repository.FollowRepository;
import com.otboo.notification.NotificationService;
import com.otboo.notification.entity.NotificationLevel;
import com.otboo.notification.entity.NotificationType;
import com.otboo.user.entity.Role;
import com.otboo.user.entity.User;
import com.otboo.user.exception.UserErrorCode;
import com.otboo.user.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;

class NotificationEventListenerTest {

    private NotificationService notificationService;
    private UserRepository userRepository;
    private FollowRepository followRepository;
    private NotificationEventListener listener;

    @BeforeEach
    void setUp() {
        notificationService = mock(NotificationService.class);
        userRepository = mock(UserRepository.class);
        followRepository = mock(FollowRepository.class);
        listener = new NotificationEventListener(notificationService, userRepository, followRepository);
    }

    @Nested
    @DisplayName("on(ClothesAttributeAddedEvent)")
    class OnClothesAttributeAdded {

        @Test
        @DisplayName("페이지네이션으로 전체 유저에게 알림을 생성한다")
        void notifiesAllUsersWithPagination() {
            UUID definitionId = UUID.randomUUID();
            ClothesAttributeAddedEvent event = ClothesAttributeAddedEvent.of(definitionId, "색상");

            UUID user1 = UUID.randomUUID();
            UUID user2 = UUID.randomUUID();

            Pageable firstPage = PageRequest.of(0, 50);
            Slice<UUID> page1 = new SliceImpl<>(List.of(user1), firstPage, true);
            Slice<UUID> page2 = new SliceImpl<>(List.of(user2), PageRequest.of(1, 50), false);

            when(userRepository.findAllIds(firstPage)).thenReturn(page1);
            when(userRepository.findAllIds(PageRequest.of(1, 50))).thenReturn(page2);

            User receiver1 = mock(User.class);
            User receiver2 = mock(User.class);
            when(userRepository.getReferenceById(user1)).thenReturn(receiver1);
            when(userRepository.getReferenceById(user2)).thenReturn(receiver2);

            listener.on(event);

            verify(notificationService).create(receiver1, null, NotificationType.CLOTHES_ATTRIBUTE_ADDED,
                definitionId.toString(), "새 의상 속성", "\"색상\" 속성이 추가됐습니다.", NotificationLevel.INFO);
            verify(notificationService).create(receiver2, null, NotificationType.CLOTHES_ATTRIBUTE_ADDED,
                definitionId.toString(), "새 의상 속성", "\"색상\" 속성이 추가됐습니다.", NotificationLevel.INFO);
        }
    }

    @Nested
    @DisplayName("on(FeedLikedEvent)")
    class OnFeedLiked {

        @Test
        @DisplayName("본인이 본인 피드를 좋아요하면 알림을 만들지 않는다")
        void doesNotNotifyWhenLikingOwnFeed() {
            UUID userId = UUID.randomUUID();
            FeedLikedEvent event = FeedLikedEvent.of(userId, userId, UUID.randomUUID());

            listener.on(event);

            verifyNoInteractions(notificationService);
            verifyNoInteractions(userRepository);
        }

        @Test
        @DisplayName("다른 사람이 좋아요하면 피드 주인에게 알림을 만든다")
        void notifiesFeedOwnerWhenOthersLike() {
            UUID feedOwnerId = UUID.randomUUID();
            UUID likerId = UUID.randomUUID();
            UUID feedId = UUID.randomUUID();
            FeedLikedEvent event = FeedLikedEvent.of(feedOwnerId, likerId, feedId);

            User receiver = mock(User.class);
            User liker = mock(User.class);
            when(liker.getName()).thenReturn("정우");
            when(userRepository.getReferenceById(feedOwnerId)).thenReturn(receiver);
            when(userRepository.findById(likerId)).thenReturn(Optional.of(liker));

            listener.on(event);

            verify(notificationService).create(receiver, liker, NotificationType.FEED_LIKED,
                feedId.toString(), "새 좋아요", "정우님이 회원님의 피드를 좋아합니다.", NotificationLevel.INFO);
        }
    }

    @Nested
    @DisplayName("on(FeedCommentedEvent)")
    class OnFeedCommented {

        @Test
        @DisplayName("댓글 작성자 이름으로 피드 주인에게 알림을 만든다")
        void notifiesFeedOwnerOnComment() {
            UUID feedOwnerId = UUID.randomUUID();
            UUID commenterId = UUID.randomUUID();
            UUID feedId = UUID.randomUUID();
            UUID commentId = UUID.randomUUID();
            FeedCommentedEvent event = FeedCommentedEvent.of(feedOwnerId, commenterId, feedId, commentId);

            User receiver = mock(User.class);
            User commenter = mock(User.class);
            when(commenter.getName()).thenReturn("댓글");
            when(userRepository.getReferenceById(feedOwnerId)).thenReturn(receiver);
            when(userRepository.findById(commenterId)).thenReturn(Optional.of(commenter));

            listener.on(event);

            verify(notificationService).create(receiver, commenter, NotificationType.FEED_COMMENTED,
                feedId.toString(), "새 댓글", "댓글님이 회원님의 피드에 댓글을 남겼습니다.", NotificationLevel.INFO);
        }
    }

    @Nested
    @DisplayName("on(FollowCreatedEvent)")
    class OnFollowCreated {

        @Test
        @DisplayName("팔로우한 사람 이름으로 팔로위에게 알림을 만든다")
        void notifiesFolloweeOnNewFollow() {
            UUID followerId = UUID.randomUUID();
            UUID followeeId = UUID.randomUUID();
            FollowCreatedEvent event = FollowCreatedEvent.of(followerId, followeeId);

            User receiver = mock(User.class);
            User follower = mock(User.class);
            when(follower.getName()).thenReturn("팔로우");
            when(userRepository.getReferenceById(followeeId)).thenReturn(receiver);
            when(userRepository.findById(followerId)).thenReturn(Optional.of(follower));

            listener.on(event);

            verify(notificationService).create(receiver, follower, NotificationType.FOLLOW_CREATED,
                followerId.toString(), "새 팔로워", "팔로우님이 회원님을 팔로우하기 시작했습니다.", NotificationLevel.INFO);
        }
    }

    @Nested
    @DisplayName("on(UserRoleChangedEvent)")
    class OnUserRoleChanged {

        @Test
        @DisplayName("변경된 권한 이름을 담아 알림을 만든다")
        void notifiesUserOnRoleChange() {
            UUID userId = UUID.randomUUID();
            UserRoleChangedEvent event = UserRoleChangedEvent.of(userId, Role.ADMIN);

            User receiver = mock(User.class);
            when(userRepository.getReferenceById(userId)).thenReturn(receiver);

            listener.on(event);

            verify(notificationService).create(receiver, null, NotificationType.ROLE_CHANGED,
                userId.toString(), "권한 변경", "회원님의 권한이 ADMIN(으)로 변경됐습니다.", NotificationLevel.INFO);
        }
    }

    @Nested
    @DisplayName("on(DirectMessageReceivedEvent)")
    class OnDirectMessageReceived {

        @Test
        @DisplayName("내용이 30자 이하면 그대로 알림에 담는다")
        void keepsFullContentWhenShort() {
            UUID senderId = UUID.randomUUID();
            UUID receiverId = UUID.randomUUID();
            UUID dmId = UUID.randomUUID();
            String content = "짧은 메시지 30자 이하 내용입니다";
            DirectMessageReceivedEvent event =
                DirectMessageReceivedEvent.of(senderId, receiverId, dmId, content);

            User receiver = mock(User.class);
            User sender = mock(User.class);
            when(sender.getName()).thenReturn("정우");
            when(userRepository.getReferenceById(receiverId)).thenReturn(receiver);
            when(userRepository.findById(senderId)).thenReturn(Optional.of(sender));

            listener.on(event);

            verify(notificationService).create(receiver, sender, NotificationType.DM_RECEIVED,
                dmId.toString(), "정우님의 새 메시지", content, NotificationLevel.INFO);
        }

        @Test
        @DisplayName("내용이 30자를 넘으면 잘라서 담는다")
        void truncatesContentWhenLong() {
            UUID senderId = UUID.randomUUID();
            UUID receiverId = UUID.randomUUID();
            UUID dmId = UUID.randomUUID();
            String content = "야호".repeat(20); // 40자, 30자 초과

            DirectMessageReceivedEvent event =
                DirectMessageReceivedEvent.of(senderId, receiverId, dmId, content);

            User receiver = mock(User.class);
            User sender = mock(User.class);
            when(sender.getName()).thenReturn("정우");
            when(userRepository.getReferenceById(receiverId)).thenReturn(receiver);
            when(userRepository.findById(senderId)).thenReturn(Optional.of(sender));

            listener.on(event);

            verify(notificationService).create(receiver, sender, NotificationType.DM_RECEIVED,
                dmId.toString(), "정우님의 새 메시지", content.substring(0, 30) + "...", NotificationLevel.INFO);
        }
    }

    @Nested
    @DisplayName("on(FollowedUserPostedEvent)")
    class OnFollowedUserPosted {

        @Test
        @DisplayName("페이지네이션으로 팔로워 전원에게 알림을 만든다")
        void notifiesAllFollowersWithPagination() {
            UUID authorId = UUID.randomUUID();
            UUID feedId = UUID.randomUUID();
            FollowedUserPostedEvent event = FollowedUserPostedEvent.of(authorId, feedId);

            User author = mock(User.class);
            when(author.getName()).thenReturn("작성자");
            when(userRepository.findById(authorId)).thenReturn(Optional.of(author));

            UUID follower1 = UUID.randomUUID();
            UUID follower2 = UUID.randomUUID();
            Pageable firstPage = PageRequest.of(0, 50);
            Slice<UUID> page1 = new SliceImpl<>(List.of(follower1), firstPage, true);
            Slice<UUID> page2 = new SliceImpl<>(List.of(follower2), PageRequest.of(1, 50), false);

            when(followRepository.findFollowerIdsByFolloweeId(authorId, firstPage)).thenReturn(page1);
            when(followRepository.findFollowerIdsByFolloweeId(authorId, PageRequest.of(1, 50))).thenReturn(page2);

            User receiver1 = mock(User.class);
            User receiver2 = mock(User.class);
            when(userRepository.getReferenceById(follower1)).thenReturn(receiver1);
            when(userRepository.getReferenceById(follower2)).thenReturn(receiver2);

            listener.on(event);

            verify(notificationService).create(receiver1, author, NotificationType.FEED_CREATED,
                feedId.toString(), "새 피드", "작성자님이 새 피드를 올렸습니다.", NotificationLevel.INFO);
            verify(notificationService).create(receiver2, author, NotificationType.FEED_CREATED,
                feedId.toString(), "새 피드", "작성자님이 새 피드를 올렸습니다.", NotificationLevel.INFO);
        }
    }

    @Nested
    @DisplayName("on(VirtualTryOnCompletedEvent)")
    class OnVirtualTryOnCompleted {

        @Test
        @DisplayName("성공하면 완료 알림을 INFO로 만든다")
        void notifiesSuccessWithInfoLevel() {
            UUID requesterId = UUID.randomUUID();
            UUID jobId = UUID.randomUUID();
            VirtualTryOnCompletedEvent event = VirtualTryOnCompletedEvent.succeeded(requesterId, jobId);

            User receiver = mock(User.class);
            when(userRepository.getReferenceById(requesterId)).thenReturn(receiver);

            listener.on(event);

            verify(notificationService).create(receiver, null, NotificationType.VIRTUAL_TRY_ON_COMPLETED,
                jobId.toString(), "가상 피팅 완료", "요청하신 가상 피팅이 완료됐습니다.", NotificationLevel.INFO);
        }

        @Test
        @DisplayName("실패하면 실패 알림을 ERROR로 만든다")
        void notifiesFailureWithErrorLevel() {
            UUID requesterId = UUID.randomUUID();
            UUID jobId = UUID.randomUUID();
            VirtualTryOnCompletedEvent event = VirtualTryOnCompletedEvent.failed(requesterId, jobId);

            User receiver = mock(User.class);
            when(userRepository.getReferenceById(requesterId)).thenReturn(receiver);

            listener.on(event);

            verify(notificationService).create(receiver, null, NotificationType.VIRTUAL_TRY_ON_COMPLETED,
                jobId.toString(), "가상 피팅 실패", "가상 피팅 생성에 실패했습니다. 다시 시도해주세요.", NotificationLevel.ERROR);
        }
    }

    @Nested
    @DisplayName("findUser")
    class FindUser {

        @Test
        @DisplayName("대상 유저가 없으면 BusinessException(NOT_FOUND)을 던진다")
        void throwsWhenActorUserNotFound() {
            UUID feedOwnerId = UUID.randomUUID();
            UUID likerId = UUID.randomUUID();
            FeedLikedEvent event = FeedLikedEvent.of(feedOwnerId, likerId, UUID.randomUUID());

            when(userRepository.getReferenceById(feedOwnerId)).thenReturn(mock(User.class));
            when(userRepository.findById(likerId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> listener.on(event))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(UserErrorCode.NOT_FOUND);
        }
    }
}
