package com.otboo.user;

import com.otboo.common.exception.BusinessException;
import com.otboo.common.pagination.CursorRequest;
import com.otboo.common.pagination.CursorResponse;
import com.otboo.common.pagination.SortDirection;
import com.otboo.common.security.AuthPrincipal;
import com.otboo.common.security.LoginUser;
import com.otboo.user.dto.ChangePasswordRequest;
import com.otboo.user.dto.ProfileDto;
import com.otboo.user.dto.ProfileUpdateRequest;
import com.otboo.user.dto.UserCreateRequest;
import com.otboo.user.dto.UserDto;
import com.otboo.user.dto.UserLockUpdateRequest;
import com.otboo.user.dto.UserRoleUpdateRequest;
import com.otboo.user.dto.UserSearchCondition;
import com.otboo.user.entity.Role;
import com.otboo.user.exception.UserErrorCode;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;
    private final ProfileService profileService;

    @PostMapping
    public ResponseEntity<UserDto> create(@Valid @RequestBody UserCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.create(request));
    }

    /** 관리자 전용. 접근 제어는 {@code SecurityConfig} 에 있다. */
    @GetMapping
    public ResponseEntity<CursorResponse<UserDto>> findAll(
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) UUID idAfter,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESCENDING") SortDirection sortDirection,
            @RequestParam(required = false) String emailLike,
            @RequestParam(required = false) Role roleEqual,
            @RequestParam(required = false) Boolean locked
    ) {
        CursorRequest request = new CursorRequest(cursor, idAfter, limit, sortBy, sortDirection);
        UserSearchCondition condition = new UserSearchCondition(emailLike, roleEqual, locked);
        return ResponseEntity.ok(userService.findAll(request, condition));
    }

    /** 다른 사람의 프로필도 볼 수 있다. 로그인만 되어 있으면 된다. */
    @GetMapping("/{userId}/profiles")
    public ResponseEntity<ProfileDto> findProfile(@PathVariable UUID userId) {
        return ResponseEntity.ok(profileService.find(userId));
    }

    /**
     * 프로필 수정. <b>본인만 가능하다.</b>
     *
     * <p>경로의 {@code userId} 를 그대로 믿으면 남의 프로필을 고칠 수 있다.
     * 로그인 주체와 대조하는 것이 이 API 의 핵심이다.
     */
    @PatchMapping(value = "/{userId}/profiles", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ProfileDto> updateProfile(
            @LoginUser AuthPrincipal me,
            @PathVariable UUID userId,
            @Valid @RequestPart("request") ProfileUpdateRequest request,
            @RequestPart(value = "image", required = false) MultipartFile image
    ) {
        requireSelf(me, userId);
        return ResponseEntity.ok(profileService.update(userId, request, image));
    }

    /** 본인만 가능하다. 관리자도 남의 비밀번호를 바꿀 수 없다. */
    @PatchMapping("/{userId}/password")
    public ResponseEntity<Void> changePassword(
            @LoginUser AuthPrincipal me,
            @PathVariable UUID userId,
            @Valid @RequestBody ChangePasswordRequest request
    ) {
        requireSelf(me, userId);
        userService.changePassword(userId, request);
        return ResponseEntity.noContent().build();
    }

    /** 관리자 전용. */
    @PatchMapping("/{userId}/role")
    public ResponseEntity<UserDto> updateRole(
            @LoginUser AuthPrincipal me,
            @PathVariable UUID userId,
            @Valid @RequestBody UserRoleUpdateRequest request
    ) {
        return ResponseEntity.ok(userService.updateRole(userId, request, me.userId()));
    }

    /** 관리자 전용. */
    @PatchMapping("/{userId}/lock")
    public ResponseEntity<UserDto> updateLock(
            @LoginUser AuthPrincipal me,
            @PathVariable UUID userId,
            @Valid @RequestBody UserLockUpdateRequest request
    ) {
        return ResponseEntity.ok(userService.updateLock(userId, request, me.userId()));
    }

    private static void requireSelf(AuthPrincipal me, UUID userId) {
        if (me.isNot(userId)) {
            throw new BusinessException(UserErrorCode.NOT_OWNER)
                    .addDetail("userId", userId.toString());
        }
    }
}
