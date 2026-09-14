package com.otboo.dm;

import com.otboo.dm.dto.DirectMessageSendRequest;
import java.security.Principal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

@Slf4j
@Controller
@RequiredArgsConstructor
public class DmSendController {

    private final DmSendService dmSendService;

    @MessageMapping("/direct-messages_send")
    public void send(Principal principal, DirectMessageSendRequest request) {
        UUID senderId = UUID.fromString(principal.getName());
        // log.info("[DM-RECEIVE] STOMP 메시지 수신, senderId={}, receiverId={}", senderId, request.receiverId());
        dmSendService.send(senderId, request.receiverId(), request.content());
    }
}
