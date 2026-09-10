package com.otboo.dm;

import com.otboo.common.pagination.CursorRequest;
import com.otboo.common.pagination.CursorResponse;
import com.otboo.common.security.AuthPrincipal;
import com.otboo.common.security.LoginUser;
import com.otboo.dm.dto.DirectMessageDto;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/direct-messages")
@RequiredArgsConstructor
public class DmController {

    private final DmService dmService;

    @GetMapping
    public ResponseEntity<CursorResponse<DirectMessageDto>> getDms(
        @LoginUser AuthPrincipal me,
        @RequestParam UUID userId,
        @ModelAttribute CursorRequest request
    ) {
        return ResponseEntity.ok(dmService.getMessages(me.userId(), userId, request));
    }
}
