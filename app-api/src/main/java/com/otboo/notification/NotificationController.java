package com.otboo.notification;

import com.otboo.common.pagination.CursorRequest;
import com.otboo.common.pagination.CursorResponse;
import com.otboo.common.security.AuthPrincipal;
import com.otboo.common.security.LoginUser;
import com.otboo.notification.dto.NotificationDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ResponseEntity<CursorResponse<NotificationDto>> getNotifications(
        @LoginUser AuthPrincipal me,
        @ModelAttribute CursorRequest request
    ) {
        return ResponseEntity.ok(notificationService.getNotifications(me.userId(), request));
    }

    @DeleteMapping("/{notificationId}")
    public ResponseEntity<Void> delete(
        @LoginUser AuthPrincipal me,
        @PathVariable UUID notificationId
    ) {
        notificationService.delete(notificationId, me.userId());
        return ResponseEntity.noContent().build();
    }
}
