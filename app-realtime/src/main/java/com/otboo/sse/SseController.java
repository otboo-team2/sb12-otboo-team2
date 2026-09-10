package com.otboo.sse;

import com.otboo.common.security.AuthPrincipal;
import com.otboo.common.security.LoginUser;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
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
}
