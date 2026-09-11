package com.otboo.notification;

import com.otboo.common.exception.BusinessException;
import com.otboo.common.pagination.CursorCodec;
import com.otboo.common.pagination.CursorRequest;
import com.otboo.common.pagination.CursorResponse;
import com.otboo.common.pagination.SortDirection;
import com.otboo.notification.broadcast.EventBroadcaster;
import com.otboo.notification.broadcast.NotificationBroadcastMessage;
import com.otboo.notification.dto.NotificationDto;
import com.otboo.notification.entity.Notification;
import com.otboo.notification.entity.NotificationLevel;
import com.otboo.notification.entity.NotificationType;
import com.otboo.notification.exception.NotificationErrorCode;
import com.otboo.notification.repository.NotificationRepository;
import com.otboo.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

    private static final String NOTIFICATION_CHANNEL = "notification-broadcast";

    private final NotificationRepository notificationRepository;
    private final EventBroadcaster eventBroadcaster;
    private final ApplicationEventPublisher eventPublisher;

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
        // log.info("[NOTIFICATION] save() 완료, id={}", notification.getId());

        eventPublisher.publishEvent(NotificationBroadcastMessage.from(notification));
    }

    @Async
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onNotificationCreated(NotificationBroadcastMessage message) {
        eventBroadcaster.broadcast(NOTIFICATION_CHANNEL, message);
        // log.info("[NOTIFICATION] broadcast() 완료, id={}", message.id());
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
