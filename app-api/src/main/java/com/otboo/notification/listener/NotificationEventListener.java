package com.otboo.notification.listener;

import com.otboo.common.event.ClothesAttributeAddedEvent;
import com.otboo.common.event.DirectMessageReceivedEvent;
import com.otboo.common.event.FeedCommentedEvent;
import com.otboo.common.event.FeedLikedEvent;
import com.otboo.common.event.FollowCreatedEvent;
import com.otboo.common.event.FollowedUserPostedEvent;
import com.otboo.common.event.UserRoleChangedEvent;
import com.otboo.common.event.VirtualTryOnCompletedEvent;
import com.otboo.notification.NotificationService;
import com.otboo.notification.entity.NotificationLevel;//import com.otboo.user.entity.User;
import com.otboo.user.entity.User;
import com.otboo.user.repository.UserRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class NotificationEventListener {

    private final NotificationService notificationService;
    private final UserRepository userRepository;

    // 미구현 이벤트
    // 새 의상 속성을 추가하는 경우
    // 팔로우하는 사용자가 새 피드를 올린 경우

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(FeedLikedEvent event) {
        if (event.feedOwnerId().equals(event.likerId())) {
            return;
        }

        User receiver = userRepository.getReferenceById(event.feedOwnerId());
        User actor = userRepository.getReferenceById(event.likerId());

        notificationService.create(
            receiver, actor, event.type(), event.feedId().toString(),
            "새 좋아요",
            "회원님의 피드를 좋아합니다.",
            NotificationLevel.INFO);
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(FeedCommentedEvent event) {
        User receiver = userRepository.getReferenceById(event.feedOwnerId());
        User actor = userRepository.getReferenceById(event.commenterId());

        notificationService.create(
            receiver, actor, event.type(), event.feedId().toString(),
            "새 댓글",
            "회원님의 피드에 댓글을 남겼습니다.",
            NotificationLevel.INFO);
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(FollowCreatedEvent event) {
        User receiver = userRepository.getReferenceById(event.followeeId());
        User actor = userRepository.getReferenceById(event.followerId());

        notificationService.create(
            receiver, actor, event.type(), event.followerId().toString(),
            "새 팔로워",
            "회원님을 팔로우하기 시작했습니다.",
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
        User actor = userRepository.getReferenceById(event.senderId());

        notificationService.create(
            receiver, actor, event.type(), event.directMessageId().toString(),
            "새 메시지",
            "새로운 메시지가 도착했습니다.",
            NotificationLevel.INFO);
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
}
