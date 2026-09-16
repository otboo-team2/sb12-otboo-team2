package com.otboo.notification.listener;

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
import com.otboo.user.entity.User;
import com.otboo.user.exception.UserErrorCode;
import com.otboo.user.repository.UserRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class NotificationEventListener {

    private final NotificationService notificationService;
    private final UserRepository userRepository;
    private final FollowRepository followRepository;

    private static final int BROADCAST_PAGE_SIZE = 50;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(ClothesAttributeAddedEvent event) {
        Pageable pageable = PageRequest.of(0, BROADCAST_PAGE_SIZE);
        Slice<UUID> slice;
        do {
            slice = userRepository.findAllIds(pageable);
            for (UUID userId : slice.getContent()) {
                User receiver = userRepository.getReferenceById(userId);
                notificationService.create(
                    receiver, null, event.type(), event.definitionId().toString(),
                    "새 의상 속성",
                    "\"" + event.attributeName() + "\" 속성이 추가됐습니다.",
                    NotificationLevel.INFO);
            }
            pageable = slice.nextPageable();
        } while (slice.hasNext());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(FeedLikedEvent event) {
        if (event.feedOwnerId().equals(event.likerId())) {
            return;
        }

        User receiver = userRepository.getReferenceById(event.feedOwnerId());
        User actor = findUser(event.likerId());

        notificationService.create(
            receiver, actor, event.type(), event.feedId().toString(),
            "새 좋아요",
            actor.getName() + "님이 회원님의 피드를 좋아합니다.",
            NotificationLevel.INFO);
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(FeedCommentedEvent event) {
        User receiver = userRepository.getReferenceById(event.feedOwnerId());
        User actor = findUser(event.commenterId());

        notificationService.create(
            receiver, actor, event.type(), event.feedId().toString(),
            "새 댓글",
            actor.getName() + "님이 회원님의 피드에 댓글을 남겼습니다.",
            NotificationLevel.INFO);
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(FollowCreatedEvent event) {
        User receiver = userRepository.getReferenceById(event.followeeId());
        User actor = findUser(event.followerId());

        notificationService.create(
            receiver, actor, event.type(), event.followerId().toString(),
            "새 팔로워",
            actor.getName() + "님이 회원님을 팔로우하기 시작했습니다.",
            NotificationLevel.INFO);
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(UserRoleChangedEvent event) {
        User receiver = userRepository.getReferenceById(event.userId());

        notificationService.create(
            receiver, null, event.type(), event.userId().toString(),
            "권한 변경",
            "회원님의 권한이 " + event.newRole() + "(으)로 변경됐습니다.",
            NotificationLevel.INFO);
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(DirectMessageReceivedEvent event) {
        User receiver = userRepository.getReferenceById(event.receiverId());
        User actor = findUser(event.senderId());

        notificationService.create(
            receiver, actor, event.type(), event.directMessageId().toString(),
            actor.getName() + "님의 새 메시지",
            preview(event.content()),
            NotificationLevel.INFO);
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(FollowedUserPostedEvent event) {
        User author = findUser(event.authorId());

        Pageable pageable = PageRequest.of(0, BROADCAST_PAGE_SIZE);
        Slice<UUID> slice;
        do {
            slice = followRepository.findFollowerIdsByFolloweeId(event.authorId(), pageable);
            for (UUID followerId : slice.getContent()) {
                User receiver = userRepository.getReferenceById(followerId);
                notificationService.create(
                    receiver, author, event.type(), event.feedId().toString(),
                    "새 피드",
                    author.getName() + "님이 새 피드를 올렸습니다.",
                    NotificationLevel.INFO);
            }
            pageable = slice.nextPageable();
        } while (slice.hasNext());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(VirtualTryOnCompletedEvent event) {
        User receiver = userRepository.getReferenceById(event.requesterId());

        if (event.succeeded()) {
            notificationService.create(
                receiver, null, event.type(), event.jobId().toString(),
                "가상 피팅 완료",
                "요청하신 가상 피팅이 완료됐습니다.",
                NotificationLevel.INFO);
        } else {
            notificationService.create(
                receiver, null, event.type(), event.jobId().toString(),
                "가상 피팅 실패",
                "가상 피팅 생성에 실패했습니다. 다시 시도해주세요.",
                NotificationLevel.ERROR);
        }
    }

    private User findUser(UUID userId) {
        return userRepository.findById(userId)
            .orElseThrow(() -> new BusinessException(UserErrorCode.NOT_FOUND));
    }

    private String preview(String content) {
        return content.length() > 30 ? content.substring(0, 30) + "..." : content;
    }
}
