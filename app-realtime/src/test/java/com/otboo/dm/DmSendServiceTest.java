package com.otboo.dm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.otboo.common.broadcast.EventBroadcaster;
import com.otboo.common.exception.BusinessException;
import com.otboo.common.exception.CommonErrorCode;
import com.otboo.dm.broadcast.DirectMessageBroadcastMessage;
import com.otboo.dm.entity.DirectMessage;
import com.otboo.dm.repository.DirectMessageRepository;
import com.otboo.user.entity.Profile;
import com.otboo.user.entity.User;
import com.otboo.user.repository.ProfileRepository;
import com.otboo.user.repository.UserRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

class DmSendServiceTest {

    private DirectMessageRepository directMessageRepository;
    private UserRepository userRepository;
    private ProfileRepository profileRepository;
    private EventBroadcaster eventBroadcaster;
    private ApplicationEventPublisher eventPublisher;
    private DmSendService dmSendService;

    @BeforeEach
    void setUp() {
        directMessageRepository = mock(DirectMessageRepository.class);
        userRepository = mock(UserRepository.class);
        profileRepository = mock(ProfileRepository.class);
        eventBroadcaster = mock(EventBroadcaster.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        dmSendService = new DmSendService(
            directMessageRepository, userRepository, profileRepository, eventBroadcaster, eventPublisher);
    }

    @Nested
    @DisplayName("send")
    class Send {

        @Test
        @DisplayName("정상 흐름이면 메시지를 저장하고 브로드캐스트 이벤트를 발행한다")
        void savesMessageAndPublishesEvent() {
            UUID senderId = UUID.randomUUID();
            UUID receiverId = UUID.randomUUID();

            User sender = mock(User.class);
            when(sender.getId()).thenReturn(senderId);
            when(sender.getName()).thenReturn("보낸사람");
            User receiver = mock(User.class);
            when(receiver.getId()).thenReturn(receiverId);
            when(receiver.getName()).thenReturn("받는사람");

            when(userRepository.findById(senderId)).thenReturn(Optional.of(sender));
            when(userRepository.findById(receiverId)).thenReturn(Optional.of(receiver));
            when(profileRepository.findByUserId(senderId)).thenReturn(Optional.empty());
            when(profileRepository.findByUserId(receiverId)).thenReturn(Optional.empty());
            when(directMessageRepository.save(any(DirectMessage.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

            dmSendService.send(senderId, receiverId, "안녕하세요");

            verify(directMessageRepository).save(any(DirectMessage.class));

            ArgumentCaptor<DirectMessageBroadcastMessage> captor =
                ArgumentCaptor.forClass(DirectMessageBroadcastMessage.class);
            verify(eventPublisher).publishEvent(captor.capture());

            DirectMessageBroadcastMessage published = captor.getValue();
            assertThat(published.content()).isEqualTo("안녕하세요");
            assertThat(published.sender().userId()).isEqualTo(senderId);
            assertThat(published.sender().name()).isEqualTo("보낸사람");
            assertThat(published.receiver().userId()).isEqualTo(receiverId);
            assertThat(published.receiver().name()).isEqualTo("받는사람");
        }

        @Test
        @DisplayName("발신자가 없으면 BusinessException(RESOURCE_NOT_FOUND)을 던진다")
        void throwsWhenSenderNotFound() {
            UUID senderId = UUID.randomUUID();
            UUID receiverId = UUID.randomUUID();
            when(userRepository.findById(senderId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> dmSendService.send(senderId, receiverId, "내용"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.RESOURCE_NOT_FOUND);

            verifyNoInteractions(directMessageRepository);
        }

        @Test
        @DisplayName("수신자가 없으면 BusinessException(RESOURCE_NOT_FOUND)을 던진다")
        void throwsWhenReceiverNotFound() {
            UUID senderId = UUID.randomUUID();
            UUID receiverId = UUID.randomUUID();
            when(userRepository.findById(senderId)).thenReturn(Optional.of(mock(User.class)));
            when(userRepository.findById(receiverId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> dmSendService.send(senderId, receiverId, "내용"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.RESOURCE_NOT_FOUND);

            verifyNoInteractions(directMessageRepository);
        }

        @Test
        @DisplayName("프로필이 있으면 프로필 이미지 URL을, 없으면 null을 담는다")
        void includesProfileImageUrlOnlyWhenProfileExists() {
            UUID senderId = UUID.randomUUID();
            UUID receiverId = UUID.randomUUID();

            User sender = mock(User.class);
            when(sender.getId()).thenReturn(senderId);
            when(sender.getName()).thenReturn("보낸사람");
            User receiver = mock(User.class);
            when(receiver.getId()).thenReturn(receiverId);
            when(receiver.getName()).thenReturn("받는사람");

            when(userRepository.findById(senderId)).thenReturn(Optional.of(sender));
            when(userRepository.findById(receiverId)).thenReturn(Optional.of(receiver));

            Profile senderProfile = mock(Profile.class);
            when(senderProfile.getProfileImageUrl()).thenReturn("https://example.com/me.png");
            when(profileRepository.findByUserId(senderId)).thenReturn(Optional.of(senderProfile));
            when(profileRepository.findByUserId(receiverId)).thenReturn(Optional.empty());

            when(directMessageRepository.save(any(DirectMessage.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

            dmSendService.send(senderId, receiverId, "안녕");

            ArgumentCaptor<DirectMessageBroadcastMessage> captor =
                ArgumentCaptor.forClass(DirectMessageBroadcastMessage.class);
            verify(eventPublisher).publishEvent(captor.capture());

            DirectMessageBroadcastMessage published = captor.getValue();
            assertThat(published.sender().profileImageUrl()).isEqualTo("https://example.com/me.png");
            assertThat(published.receiver().profileImageUrl()).isNull();
        }
    }

    @Nested
    @DisplayName("onMessageSent")
    class OnMessageSent {

        @Test
        @DisplayName("전달받은 메시지를 dm-broadcast 채널로 브로드캐스트한다")
        void broadcastsMessage() {
            DirectMessageBroadcastMessage message = new DirectMessageBroadcastMessage(
                UUID.randomUUID(), Instant.now(), "dmKey",
                new DirectMessageBroadcastMessage.UserSummary(UUID.randomUUID(), "발신", null),
                new DirectMessageBroadcastMessage.UserSummary(UUID.randomUUID(), "수신", null),
                "내용");

            dmSendService.onMessageSent(message);

            verify(eventBroadcaster).broadcast("dm-broadcast", message);
        }
    }
}
