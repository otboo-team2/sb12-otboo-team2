package com.otboo.notification;

import com.otboo.common.exception.BusinessException;
import com.otboo.common.pagination.CursorCodec;
import com.otboo.common.pagination.CursorRequest;
import com.otboo.common.pagination.CursorResponse;
import com.otboo.notification.dto.NotificationDto;
import com.otboo.notification.entity.Notification;
import com.otboo.notification.entity.NotificationLevel;
import com.otboo.notification.entity.NotificationType;
import com.otboo.notification.exception.NotificationErrorCode;
import com.otboo.notification.repository.NotificationRepository;
import com.otboo.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

    private final NotificationRepository notificationRepository;

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

        return CursorResponse.of(notificationDtos, request, totalCount, NotificationDto::createdAt, NotificationDto::id);
    }

    @Transactional
    public void delete(UUID notificationId, UUID receiverId) {
        int deleted = notificationRepository.deleteByIdAndReceiverId(notificationId, receiverId);
        if (deleted == 0) {
            throw new BusinessException(NotificationErrorCode.NOT_FOUND);
        }
    }
}
