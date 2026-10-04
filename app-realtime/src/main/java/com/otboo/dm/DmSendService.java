package com.otboo.dm;

import com.otboo.common.exception.BusinessException;
import com.otboo.common.exception.CommonErrorCode;
import com.otboo.dm.broadcast.DirectMessageBroadcastMessage;
import com.otboo.dm.broadcast.DirectMessageBroadcastMessage.UserSummary;
import com.otboo.dm.entity.DirectMessage;
import com.otboo.dm.repository.DirectMessageRepository;
import com.otboo.outbox.OutboxAppender;
import com.otboo.user.entity.Profile;
import com.otboo.user.entity.User;
import com.otboo.user.repository.ProfileRepository;
import com.otboo.user.repository.UserRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DmSendService {

    private static final String DM_CHANNEL = "dm-broadcast";

    private final DirectMessageRepository directMessageRepository;
    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;
    private final OutboxAppender outboxAppender;

    @Transactional
    public void send(UUID senderId, UUID receiverId, String content) {
        User sender = findUser(senderId);
        User receiver = findUser(receiverId);

        DirectMessage message = directMessageRepository.save(
            DirectMessage.create(sender, receiver, content));

        outboxAppender.append(DM_CHANNEL, message.getDmKey(),
            DirectMessageBroadcastMessage.from(message, summaryOf(sender), summaryOf(receiver)));
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
