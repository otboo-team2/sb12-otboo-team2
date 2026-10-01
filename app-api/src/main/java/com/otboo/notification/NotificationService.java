package com.otboo.notification;

import com.otboo.common.exception.BusinessException;
import com.otboo.common.pagination.CursorCodec;
import com.otboo.common.pagination.CursorRequest;
import com.otboo.common.pagination.CursorResponse;
import com.otboo.common.pagination.SortDirection;
import com.otboo.notification.broadcast.NotificationBroadcastMessage;
import com.otboo.notification.dto.NotificationDto;
import com.otboo.notification.entity.Notification;
import com.otboo.notification.entity.NotificationLevel;
import com.otboo.notification.entity.NotificationType;
import com.otboo.notification.exception.NotificationErrorCode;
import com.otboo.notification.repository.NotificationRepository;
import com.otboo.outbox.OutboxAppender;
import com.otboo.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

    private static final String NOTIFICATION_CHANNEL = "notification-broadcast";

    private final NotificationRepository notificationRepository;
    private final OutboxAppender outboxAppender;

    /**
     * 알림 저장과 발행 메시지 등록을 한 트랜잭션으로 묶는다(Transactional Outbox).
     * 실제 Kafka 발행은 커밋 후 OutboxRelay 가 한다. Kafka 가 멈춰 있어도 메시지는 outbox 에 남는다.
     */
    @Transactional
    public void create(
        User receiver, User actor,
        NotificationType type, String relatedEntityId,
        String title, String content,
        NotificationLevel level
    ) {
        Notification notification = Notification.create(
            receiver, actor,
            type, relatedEntityId,
            title, content,
            level);
        notificationRepository.save(notification);

        outboxAppender.append(NOTIFICATION_CHANNEL,
            notification.getReceiver().getId().toString(),
            NotificationBroadcastMessage.from(notification));
    }

    /**
     * 같은 대상(relatedEntityId)으로 이미 만든 알림이 있으면 건너뛴다.
     * outbox 는 at-least-once 라 같은 메시지가 두 번 올 수 있다(DM 등).
     */
    @Transactional
    public void createOnce(
        User receiver, User actor,
        NotificationType type, String relatedEntityId,
        String title, String content,
        NotificationLevel level
    ) {
        if (notificationRepository.existsByReceiverIdAndTypeAndRelatedEntityId(
            receiver.getId(), type, relatedEntityId)) {
            log.info("notification_duplicate_skipped type={} relatedEntityId={}", type, relatedEntityId);
            return;
        }
        create(receiver, actor, type, relatedEntityId, title, content, level);
    }

    @Transactional
    public void createAll(
        List<User> receivers, User actor,
        NotificationType type, String relatedEntityId,
        String title, String content,
        NotificationLevel level
    ) {
        List<Notification> notifications = receivers.stream()
            .map(receiver -> Notification.create(
                receiver, actor, type, relatedEntityId, title, content, level))
            .toList();
        notificationRepository.saveAll(notifications);

        outboxAppender.appendAll(NOTIFICATION_CHANNEL,
            notifications.stream().map(NotificationBroadcastMessage::from).toList(),
            message -> message.receiverId().toString());
    }

    public CursorResponse<NotificationDto> getNotifications(UUID receiverId, CursorRequest request) {
        Pageable pageable = PageRequest.of(0, request.fetchSize());

        List<Notification> notifications = notificationRepository.findByReceiverId(
            receiverId,
            CursorCodec.asInstant(request.cursor()),
            request.idAfter(),
            pageable);

        List<NotificationDto> notificationDtos = notifications.stream().map(NotificationDto::from).toList();
        long totalCount = notificationRepository.countByReceiverId(receiverId);

        CursorResponse<NotificationDto> response =
            CursorResponse.of(notificationDtos, request, totalCount, NotificationDto::createdAt, NotificationDto::id);

        return new CursorResponse<>(
            response.data(),
            response.nextCursor(),
            response.nextIdAfter(),
            response.hasNext(),
            response.totalCount(),
            "createdAt",
            SortDirection.DESCENDING
        );
    }

    @Transactional
    public void delete(UUID notificationId, UUID receiverId) {
        int deleted = notificationRepository.deleteByIdAndReceiverId(notificationId, receiverId);
        if (deleted == 0) {
            if (!notificationRepository.existsById(notificationId)) {
                throw new BusinessException(NotificationErrorCode.NOT_FOUND);
            }
            throw new BusinessException(NotificationErrorCode.ACCESS_DENIED);
        }
    }
}
