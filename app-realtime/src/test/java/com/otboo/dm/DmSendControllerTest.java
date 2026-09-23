package com.otboo.dm;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.otboo.dm.dto.DirectMessageSendRequest;
import java.security.Principal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DmSendControllerTest {

    private final DmSendService dmSendService = mock(DmSendService.class);
    private final DmSendController controller = new DmSendController(dmSendService);

    @Test
    void delegatesToServiceWithParsedSenderId() {
        UUID senderId = UUID.randomUUID();
        UUID receiverId = UUID.randomUUID();
        Principal principal = senderId::toString;
        DirectMessageSendRequest request = new DirectMessageSendRequest(receiverId, "안녕하세요");

        controller.send(principal, request);

        verify(dmSendService).send(senderId, receiverId, "안녕하세요");
    }
}
