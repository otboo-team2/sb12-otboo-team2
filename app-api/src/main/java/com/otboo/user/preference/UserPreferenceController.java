package com.otboo.user.preference;

import com.otboo.common.security.AuthPrincipal;
import com.otboo.common.security.LoginUser;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users/{userId}/preferences")
public class UserPreferenceController {

    private final UserPreferenceService preferenceService;

    @GetMapping
    public ResponseEntity<List<UserPreferenceDto>> find(
            @LoginUser AuthPrincipal me,
            @PathVariable UUID userId) {
        return ResponseEntity.ok(preferenceService.find(me, userId));
    }

    @PutMapping
    public ResponseEntity<List<UserPreferenceDto>> replace(
            @LoginUser AuthPrincipal me,
            @PathVariable UUID userId,
            @Valid @RequestBody UserPreferenceUpdateRequest request) {
        return ResponseEntity.ok(preferenceService.replace(me, userId, request));
    }
}
