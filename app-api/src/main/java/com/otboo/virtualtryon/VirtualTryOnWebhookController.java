package com.otboo.virtualtryon;

import com.otboo.virtualtryon.client.FashnStatusResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/fittings/webhook")
public class VirtualTryOnWebhookController {

    private final VirtualTryOnPoller poller;
    private final byte[] token;

    public VirtualTryOnWebhookController(
        VirtualTryOnPoller poller,
        @Value("${otboo.virtual-try-on.webhook-token:}") String token
    ) {
        this.poller = poller;
        this.token = token.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * FASHN 이 생성이 끝나면 호출한다. 로그인 토큰이 없는 외부 호출이라 인증 대신 URL 의 토큰을 비교한다.
     * 결과 저장에 2초쯤 걸려서 바로 200 을 주고 처리는 비동기로 넘긴다(늦게 응답하면 FASHN 이 재전송한다).
     */
    @PostMapping("/{token}")
    public ResponseEntity<Void> receive(
        @PathVariable("token") String token,
        @RequestBody FashnStatusResponse payload
    ) {
        if (this.token.length == 0
            || !MessageDigest.isEqual(this.token, token.getBytes(StandardCharsets.UTF_8))) {
            return ResponseEntity.notFound().build();
        }
        poller.handleWebhook(payload);
        return ResponseEntity.ok().build();
    }
}
