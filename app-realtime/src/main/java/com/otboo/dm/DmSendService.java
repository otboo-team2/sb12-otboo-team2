package com.otboo.dm;

import com.otboo.common.broadcast.EventBroadcaster;
import com.otboo.common.exception.BusinessException;
import com.otboo.common.exception.CommonErrorCode;
import com.otboo.dm.broadcast.DirectMessageBroadcastMessage;
import com.otboo.dm.broadcast.DirectMessageBroadcastMessage.UserSummary;
import com.otboo.dm.entity.DirectMessage;
import com.otboo.dm.repository.DirectMessageRepository;
import com.otboo.user.entity.Profile;
import com.otboo.user.entity.User;
import com.otboo.user.repository.ProfileRepository;
import com.otboo.user.repository.UserRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DmSendService {

    private static final String DM_CHANNEL = "dm-broadcast";

    private final DirectMessageRepository directMessageRepository;
    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;
    private final EventBroadcaster eventBroadcaster;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public void send(UUID senderId, UUID receiverId, String content) {
        User sender = findUser(senderId);
        User receiver = findUser(receiverId);

        DirectMessage message = directMessageRepository.save(
            DirectMessage.create(sender, receiver, content));
        // log.info("[DM-SEND] save() 완료, id={}, dmKey={}", message.getId(), message.getDmKey());

        eventPublisher.publishEvent(
            DirectMessageBroadcastMessage.from(message, summaryOf(sender), summaryOf(receiver)));
        // log.info("[DM-SEND] publishEvent() 호출됨, id={}", message.getId());
    }

    @Async
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMessageSent(DirectMessageBroadcastMessage message) {
        // log.info("[DM-SEND] AFTER_COMMIT 리스너 실행됨, id={}", message.id());
        eventBroadcaster.broadcast(DM_CHANNEL, message);
        // log.info("[DM-SEND] broadcast() 완료, id={}", message.id());
    }

    private User findUser(UUID userId) {
        return userRepository.findById(userId)
            .orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND)
                .addDetail("userId", userId.toString()));
    }

    private UserSummary summaryOf(User user) {
        String profileImageUrl = profileRepository.findByUserId(user.getId())
            .map(Profile::getProfileImageUrl)
            .orElse(null);
        return new UserSummary(user.getId(), user.getName(), profileImageUrl);
    }
}
