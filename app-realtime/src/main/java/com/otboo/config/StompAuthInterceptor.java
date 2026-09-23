package com.otboo.config;

import com.otboo.common.exception.BusinessException;
import com.otboo.common.exception.CommonErrorCode;
import com.otboo.common.security.AuthPrincipal;
import com.otboo.common.security.JwtProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StompAuthInterceptor implements ChannelInterceptor {

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";
    private static final String DM_DESTINATION_PREFIX = "/sub/direct-messages_";

    private final JwtProvider jwtProvider;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
            MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor == null) {
            return message;
        }

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            authenticate(accessor);
        } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            authorizeDmSubscription(accessor);
        }

        return message;
    }

    private void authenticate(StompHeaderAccessor accessor) {
        String token = resolveToken(accessor.getFirstNativeHeader(HEADER));

        AuthPrincipal principal = jwtProvider.parse(token)
            .orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHORIZED));

        accessor.setUser(() -> principal.userId().toString());
    }

    private void authorizeDmSubscription(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (destination == null || !destination.startsWith(DM_DESTINATION_PREFIX)) {
            return;
        }

        String userId = accessor.getUser() != null ? accessor.getUser().getName() : null;
        if (userId == null) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
        }

        String dmKey = destination.substring(DM_DESTINATION_PREFIX.length());
        String[] participantIds = dmKey.split("_");
        boolean isParticipant = participantIds.length == 2
            && (participantIds[0].equals(userId) || participantIds[1].equals(userId));

        if (!isParticipant) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
    }

    private String resolveToken(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith(PREFIX)) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
        }
        return authorizationHeader.substring(PREFIX.length());
    }
}
