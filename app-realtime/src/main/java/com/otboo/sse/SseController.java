package com.otboo.sse;

import com.otboo.common.security.AuthPrincipal;
import com.otboo.common.security.LoginUser;

import java.io.IOException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Slf4j
@RestController
@RequiredArgsConstructor
public class SseController {

    private final SseDeliveryService sseDeliveryService;

    @GetMapping(value = "/api/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter connect(
        @LoginUser AuthPrincipal me,
        @RequestParam(value = "LastEventId", required = false) UUID lastEventIdParam,
        @RequestHeader(value = "Last-Event-ID", required = false) UUID lastEventIdHeader
    ) {
        // log.info("[SSE-CONNECT] 요청 들어옴, userId={}", me.userId());

        UUID lastEventId = lastEventIdParam != null
            ? lastEventIdParam
            : lastEventIdHeader;

        return sseDeliveryService.connect(me.userId(), lastEventId);
    }

    /**
     * SSE 클라이언트가 이미 연결을 끊은 뒤 서버(하트비트 등)가 거기에 쓰려고 할 때 발생.
     * AsyncRequestNotUsableException으로 깔끔하게 오는 경우도 있고,
     * 그냥 IOException으로 튀어나오는 경우도 있어서 둘 다 잡아야 한다.
     * 이미 못 쓰는 연결이라 응답을 시도하지 않는다.
     */
    @ExceptionHandler({AsyncRequestNotUsableException.class, IOException.class})
    public void handleSseClientDisconnected(Exception e) {
        // 이미 끊긴 연결이라 응답을 시도하지 않고 그냥 넘어간다
    }
}
